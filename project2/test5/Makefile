TARGET=test5

all: $(TARGET).bc outputs

include ../pipair.mak

$(TARGET).bc: openssl-cli.bc.orig
	cp $< $@

clean:
	rm -rf $(TARGET).bc
	rm -rf $(TARGET)_*_*.out


