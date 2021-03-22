/* This program is a modified form of tinycrypt, which uses enchancements I think are likely to be
 * good.  In particular, modcrypt differs from tinycrypt in that it:
 * - Reduces nonce value to 20 bytes - 160 bits should be enough.
 * - Reduces discarded bytes to 512 - There's no evidence on the net that more are needed.
 * - Simplifies mixing of the password and nonce values with the key, since throwing away the
 *   first 512 bytes mixes them up anyway. */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include "minilzo.h"
#include "encrypt.c" /* Note that we include the source directy for speed */

static char *password;
static unsigned char *fileBuffer;
static unsigned long fileBufferSize;
static FILE *randFile;
static int encrypting;
static int dontCompress;

/* Clear a string */
static void clearString(
    char *p)
{
    while(*p) {
        *p++ = '\0';
    }
}

/* Get the password from the user.  If encrypting, get it twice, and return 0 if they differ.  On
 * success, allocates space for password, and the caller must later clear it and free it. */
static int getPassWord(int encrypting) {
    char *initialPasswd;

    initialPasswd = getpass("Password: ");
    password = calloc(strlen(initialPasswd) + 1, sizeof(unsigned char));
    strcpy(password, initialPasswd);
    clearString(initialPasswd);
    if(!encrypting) {
        return 1;
    }
    initialPasswd = getpass("Re-enter password: ");
    if(strcmp(initialPasswd, password)) {
        clearString(initialPasswd);
        clearString(password);
        free(password);
        return 0;
    }
    clearString(initialPasswd);
    return 1;
}

/* Find the size of a file */
static unsigned long findFileSize(FILE *file) {
    unsigned long fileSize;

    fseek(file, 0, SEEK_END);
    fileSize = ftell(file);
    fseek(file, 0, SEEK_SET);
    return fileSize;
}

/* Read the file into a file buffer. */
static void readFile(FILE *inputFile) {
    fileBufferSize = findFileSize(inputFile);
    fileBuffer = (unsigned char *)malloc(fileBufferSize);
    if(fread((void *)fileBuffer, 1, fileBufferSize, inputFile) != fileBufferSize) {
        printf("Unable to read the entire file.\n");
        exit(1);
    }
}

/* Write out fileBuffer to the file. */
static void writeFile(FILE *outputFile) {
    if(fwrite((void *)fileBuffer, 1, fileBufferSize, outputFile) != fileBufferSize) {
        printf("Unable to write the entire file.\n");
        exit(1);
    }
}

/* Compress the file using LZO, the fast compression library. */
static void compressFile(void) {
    unsigned long compressedBufferSize = fileBufferSize + (fileBufferSize >> 6) + 19;
    unsigned char *compressedBuffer = (unsigned char *)malloc(compressedBufferSize);
    unsigned char *workMem = (unsigned char *)malloc(LZO1X_1_MEM_COMPRESS);
    lzo_uint newBufferSize;

    lzo1x_1_compress(fileBuffer, fileBufferSize, compressedBuffer, &newBufferSize, workMem);
    free(fileBuffer);
    free(workMem);
    fileBufferSize = newBufferSize;
    fileBuffer = compressedBuffer;
}

/* Decompress the file using LZO, the fast compression library. */
static void decompressFile(unsigned long originalFileLength) {
    unsigned char *uncompressedBuffer = (unsigned char *)malloc(originalFileLength);
    lzo_uint newLength;

    lzo1x_decompress(fileBuffer, fileBufferSize, uncompressedBuffer, &newLength,NULL);
    if(newLength != originalFileLength) {
        printf("Decryption failed... did you type the right password?\n");
        exit(1);
    }
    free(fileBuffer);
    fileBuffer = uncompressedBuffer;
    fileBufferSize = newLength;
}

/* Encrypt the file with a random key XOR-ed with the user's munged key. Prepend
 * the nounce, 4 bytes for how many kilobytes to drop from ARC4, and the
 * uncompressed file length, then encrypt it.  We drop enough kilobytes of ARC4
 * data to consume 1 CPU second.  When decrypting, check for 8 zeros */
static void encryptFile(unsigned long originalFileLength) {
    unsigned char *encryptedBuffer = (unsigned char *)malloc(fileBufferSize + NONCE_LENGTH + 8);
    unsigned long xChar, loops;
    unsigned char *fileBufferp = fileBuffer;
    unsigned char *encryptedBufferp = encryptedBuffer;

    for(xChar = 0; xChar < NONCE_LENGTH; xChar++) {
        *encryptedBufferp++ = getc(randFile);
    }
    initKey(password, encryptedBuffer, NONCE_LENGTH); /* Nonce is in start of encrypted buffer */
    for(loops = 0; loops < DIFFICULTY; loops++) {
        throwAwaySomeBytes(DISCARD_BYTES);
    }
    for(xChar = 0; xChar < 4; xChar++) {
        *encryptedBufferp++ = hashChar((char)originalFileLength);
        originalFileLength >>= 8;
    }
    for(xChar = 0; xChar < fileBufferSize; xChar++) {
        *encryptedBufferp++ = hashChar(*fileBufferp++);
    }
    for(xChar = 0; xChar < 4; xChar++) {
        *encryptedBufferp++ = hashChar('\0');
    }
    free(fileBuffer);
    fileBuffer = encryptedBuffer;
    fileBufferSize += NONCE_LENGTH + 12;
}

/* Decrypt the file using the first NONCE bytes as the key.  Return the original file length */
static unsigned long decryptFile(void) {
    unsigned char *decryptedBuffer = (unsigned char *)malloc(fileBufferSize - NONCE_LENGTH - 8);
    unsigned char *fileBufferp = fileBuffer + NONCE_LENGTH;
    unsigned char *decryptedBufferp = decryptedBuffer;
    unsigned long originalFileLength = 0;
    unsigned long xChar, loops;

    initKey(password, fileBuffer, NONCE_LENGTH); /* Nonce is at start of file buffer */
    for(loops = 0; loops < DIFFICULTY; loops++) {
        throwAwaySomeBytes(DISCARD_BYTES);
    }
    for(xChar = 0; xChar < 4; xChar++) {
        originalFileLength >>= 8;
        originalFileLength |= hashChar(*fileBufferp++) << 24;
    }
    for(xChar = NONCE_LENGTH + 12; xChar < fileBufferSize; xChar++) {
        *decryptedBufferp++ = hashChar(*fileBufferp++);
    }
    /* Now, check for the correct password by reading 4 zeros */
    for(xChar = 0; xChar < 4; xChar++) {
        if(hashChar(*fileBufferp++) != '\0') {
            printf("Incorrect password.\n");
            exit(1);
        }
    }
    free(fileBuffer);
    fileBuffer = decryptedBuffer;
    fileBufferSize -= NONCE_LENGTH + 8;
    return originalFileLength;
}

void usage(
    char *progName)
{
    printf("Usage: %s [-c] file\n"
        "    -c -- Don't compress/uncompress the file, just encrypt/decrypt it\n", progName);
    exit(1);
}

int main(int argc, char**argv) {
    char *extension, *inFileName, *outFileName;
    FILE *inputFile, *outputFile;
    unsigned long originalFileLength;
    int xArg = 1;

    dontCompress = 0;
    while(xArg < argc && *argv[xArg] == '-') {
        if(!strcmp(argv[xArg], "-c")) {
            dontCompress = 1;
        } else {
            usage(argv[0]);
        }
        xArg++;
    }
    if(argc - xArg != 1) {
        usage(argv[0]);
    }
    inFileName = argv[xArg++];
    outFileName = calloc(strlen(inFileName) + 5, sizeof(char));
    strcpy(outFileName, inFileName);
    extension = strrchr(outFileName, '.');
    if(extension == NULL || strcasecmp(extension, ".enc")) {
        strcat(outFileName, ".enc");
        encrypting = 1;
    } else {
        *extension = '\0';
        encrypting = 0;
    }
    inputFile = fopen(inFileName, "rb");
    if(inputFile == NULL) {
        printf("Could not open file %s for reading\n", inFileName);
        exit(1);
    }
    if(lzo_init() != LZO_E_OK) {
        printf("lzo_init() failed !!!\n");
        exit(1);
    }
    randFile = fopen("/dev/urandom", "r");
    if(randFile == NULL) {
        printf("Unable to open random number source.\n");
        exit(1);
    }
    if(!getPassWord(encrypting)) {
        printf("Passwords to not match.\n");
        exit(1);
    }
    readFile(inputFile);
    if(encrypting) {
        originalFileLength = fileBufferSize;
        if(!dontCompress) {
            compressFile();
        }
        encryptFile(originalFileLength);
        clearString(password);
        free(password);
        clearKey();
        outputFile = fopen(outFileName, "wb");
        if(outputFile == NULL) {
            printf("Could not open file %s for writing\n", outFileName);
            exit(1);
        }
        writeFile(outputFile);
    } else {
        originalFileLength = decryptFile();
        clearString(password);
        free(password);
        clearKey();
        if(!dontCompress) {
            decompressFile(originalFileLength);
        }
        outputFile = fopen(outFileName, "wb");
        if(outputFile == NULL) {
            printf("Could not open file %s for writing\n", outFileName);
            exit(1);
        }
        writeFile(outputFile);
    }
    fclose(outputFile);
    fclose(inputFile);
    fclose(randFile);
    return 0;
}
