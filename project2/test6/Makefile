TARGET=test6

all: $(TARGET).bc outputs

include ../pipair.mak

$(TARGET).bc: node.bc.orig
	cp $< $@

clean:
	rm -rf $(TARGET).bc
	rm -rf $(TARGET)_*_*.out


