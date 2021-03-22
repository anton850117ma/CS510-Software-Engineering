import numpy as np
import pandas as pd
import pickle
import re
import os
import tensorflow_datasets as tfds
from tensorflow.keras.utils import Sequence
from tensorflow.keras.layers import Dense, Input
from tensorflow.keras.layers import Embedding, LSTM, GRU, Bidirectional, Layer
from tensorflow.keras.models import Model
from tensorflow.keras import backend as K
from tensorflow.keras import initializers
from tensorflow.keras.callbacks import ModelCheckpoint
import tensorflow as tf
from sklearn.metrics import roc_curve, auc
import matplotlib.pyplot as plt

MAX_SENT_LENGTH = 1000
EMBEDDING_DIM = 100

with open('data/y_train.pickle', 'rb') as handle:
    Y_train = pickle.load(handle)
with open('data/y_test.pickle', 'rb') as handle:
    Y_test = pickle.load(handle)
with open('data/y_valid.pickle', 'rb') as handle:
    Y_valid = pickle.load(handle)

with open('data/x_train.pickle', 'rb') as handle:
    X_train = pickle.load(handle)
with open('data/x_test.pickle', 'rb') as handle:
    X_test = pickle.load(handle)
with open('data/x_valid.pickle', 'rb') as handle:
    X_valid = pickle.load(handle)
with open('data/vocab_set.pickle', 'rb') as handle:
    vocabulary_set = pickle.load(handle)

X_train = X_train[:10000]
Y_train = Y_train[:10000]
X_test = X_test[:5000]
Y_test = Y_test[:5000]
X_valid = X_valid[:5000]
Y_valid = Y_valid[:5000]

encoder = tfds.features.text.TokenTextEncoder(vocabulary_set)

embedding_layer = Embedding(encoder.vocab_size + 1,
                            EMBEDDING_DIM,
                            input_length=MAX_SENT_LENGTH,
                            mask_zero=True)

class AttLayer(Layer):
    def __init__(self, attention_dim):
        self.init = initializers.get('normal')
        self.supports_masking = True
        self.attention_dim = attention_dim
        super(AttLayer, self).__init__()

    def build(self, input_shape):
        assert len(input_shape) == 3
        self.W = K.variable(self.init((input_shape[-1], self.attention_dim)), name='W')
        self.b = K.variable(self.init((self.attention_dim, )), name='b')
        self.u = K.variable(self.init((self.attention_dim, 1)), name='u')
        self.trainable_weight = [self.W, self.b, self.u]
        super(AttLayer, self).build(input_shape)

    def compute_mask(self, inputs, mask=None):
        return None

    def call(self, x, mask=None):
        # size of x :[batch_size, sel_len, attention_dim]
        # size of u :[batch_size, attention_dim]
        # uit = tanh(xW+b)
        uit = K.tanh(K.bias_add(K.dot(x, self.W), self.b))
        ait = K.dot(uit, self.u)
        ait = K.squeeze(ait, -1)

        ait = K.exp(ait)

        if mask is not None:
            # Cast the mask to floatX to avoid float64 upcasting in theano
            ait *= K.cast(mask, K.floatx())
        ait /= K.cast(K.sum(ait, axis=1, keepdims=True) + K.epsilon(), K.floatx())
        ait = K.expand_dims(ait)
        weighted_input = x * ait
        output = K.sum(weighted_input, axis=1)

        return output

    def compute_output_shape(self, input_shape):
        return (input_shape[0], input_shape[-1])

    def get_config(self):
        config = super().get_config().copy()
        config.update({
            'init': self.init,
            'supports_masking': self.supports_masking,
            'attention_dim': self.attention_dim
        })
        return config


sentence_input = Input(shape=(MAX_SENT_LENGTH,))
embedded_sequences = embedding_layer(sentence_input)
l_lstm = Bidirectional(GRU(100, return_sequences=True))(embedded_sequences)
l_att = AttLayer(100)(l_lstm)
preds = Dense(1, activation='softmax')(l_att)
model = Model(sentence_input, preds)

model.compile(loss='binary_crossentropy',
              optimizer=tf.keras.optimizers.Adam(1e-4),
              metrics=['accuracy'])

model.summary()
batch_size = 50

class CustomGenerator(Sequence):
    def __init__(self, text, labels, batch_size, num_steps=None):
        self.text, self.labels = text, labels
        self.batch_size = batch_size
        self.len = np.ceil(len(self.text) / float(self.batch_size)).astype(np.int64)
        if num_steps:
            self.len = min(num_steps, self.len)
    def __len__(self):
        return self.len
    def __getitem__(self, idx):
        batch_x = self.text[idx * self.batch_size:(idx + 1) * self.batch_size]
        batch_y = self.labels[idx * self.batch_size:(idx + 1) * self.batch_size]
        return batch_x, batch_y


train_gen = CustomGenerator(X_train, Y_train, batch_size)
valid_gen = CustomGenerator(X_valid, Y_valid, batch_size)
test_gen = CustomGenerator(X_test, Y_test, batch_size)

his1 = model.fit_generator(
                    generator=train_gen,
                    epochs=1,
                    validation_data=valid_gen)


predIdxs = model.predict_generator(test_gen, verbose=1)

fpr, tpr, _ = roc_curve(Y_test, predIdxs)
roc_auc = auc(fpr, tpr)

plt.figure()
lw = 2
plt.plot(fpr, tpr, color='darkorange', lw=lw, label='ROC curve (area = %0.2f)' % roc_auc)
plt.plot([0, 1], [0, 1], color='navy', lw=lw, linestyle='--')
plt.xlim([0.0, 1.0])
plt.ylim([0.0, 1.05])
plt.xlabel('False Positive Rate')
plt.ylabel('True Positive Rate')
plt.title('Receiver operating characteristic example')
plt.legend(loc="lower right")

plt.savefig('auc_model_han.png')