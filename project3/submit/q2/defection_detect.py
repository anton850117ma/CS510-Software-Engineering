# /homes/cs510/project-3/data as data path
# source /homes/cs510/project-3/venv/bin/activate
# nvidia-smi
# module load cuda/10.0
# source /homes/cs510/project-3/venv-gpu/bin/activate
# python defection_detect.py

import pickle
import pandas as pd
import numpy as np
import tensorflow_datasets as tfds
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences
from tensorflow.keras.callbacks import ModelCheckpoint
from tensorflow.keras.utils import Sequence
from sklearn.metrics import roc_curve, auc
import matplotlib.pyplot as plt
import os
import time

from ind_rnn import IndRNNCell, RNN
from tensorflow.keras.layers import Dense
from tensorflow.keras.optimizers import Adam

os.environ['CUDA_VISIBLE_DEVICES'] = '1'

with open('../LSTM/data/y_train.pickle', 'rb') as handle:
    Y_train = pickle.load(handle)
with open('../LSTM/data/y_test.pickle', 'rb') as handle:
    Y_test = pickle.load(handle)
with open('../LSTM/data/y_valid.pickle', 'rb') as handle:
    Y_valid = pickle.load(handle)

with open('../LSTM/data/x_train.pickle', 'rb') as handle:
    X_train = pickle.load(handle)
with open('../LSTM/data/x_test.pickle', 'rb') as handle:
    X_test = pickle.load(handle)
with open('../LSTM/data/x_valid.pickle', 'rb') as handle:
    X_valid = pickle.load(handle)
with open('../LSTM/data/vocab_set.pickle', 'rb') as handle:
    vocabulary_set = pickle.load(handle)
X_train = X_train[:10000]
Y_train = Y_train[:10000]
X_test = X_test[:5000]
Y_test = Y_test[:5000]
X_valid = X_valid[:5000]
Y_valid = Y_valid[:5000]

# Encode training, valid and test instances
encoder = tfds.features.text.TokenTextEncoder(vocabulary_set)

# # Model Definition
# model = tf.keras.Sequential([
#     tf.keras.layers.Embedding(encoder.vocab_size, 64),
#     tf.keras.layers.Bidirectional(tf.keras.layers.LSTM(64, return_sequences=True)),
#     tf.keras.layers.Bidirectional(tf.keras.layers.LSTM(32)),
#     tf.keras.layers.Dense(64, activation='relu'),
#     tf.keras.layers.Dropout(0.5),
#     tf.keras.layers.Dense(1, activation='sigmoid')
# ])
#
# model.compile(loss='binary_crossentropy',
#               optimizer=tf.keras.optimizers.Adam(1e-4),
#               metrics=['accuracy'])
#
# model.summary()
# batch_size = 16

batch_size = 100
epochs = 3
hidden_units = 128
learning_rate = 1e-2

cells = [IndRNNCell(hidden_units),
         IndRNNCell(hidden_units)]

print('Evaluate IRNN...')
model = tf.keras.Sequential([tf.keras.layers.Embedding(encoder.vocab_size, 64), RNN(cells, input_shape=X_train.shape[:]), tf.keras.layers.Dense(1, activation='sigmoid')])
# print(X_train[:10])

rmsprop = Adam(lr=learning_rate, amsgrad=True)

model.compile(loss='binary_crossentropy',
              optimizer=rmsprop,
              metrics=['accuracy'])

model.summary()


# Building generators
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


# Training the model
checkpointer = ModelCheckpoint('weights/IndRNN_model-{epoch:02d}-{val_loss:.5f}.hdf5',
                               monitor='val_loss',
                               verbose=1,
                               save_best_only=True,
                               mode='min')

# callback_list = [checkpointer]  # , , reduce_lr
his1 = model.fit_generator(
    generator=train_gen,
    epochs=3,
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

ts = time.time()
plt.savefig('IndRNN_auc_model.png')
