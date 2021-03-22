/* Based on ARC4.  See http://en.wikipedia.org/wiki/RC4. */
#include <stdio.h>
#include <string.h>
#include "encrypt.h"

static unsigned char temp;
#define swap(a, b) (temp = (a), (a) = (b), (b) = temp)

static unsigned char S[KEY_LENGTH]; /* Note: KEY_LENGTH MUST be 256! */
static unsigned char i, j;
static int xChar;

/* Hash the character using the current key position, and modify the key. */
static unsigned char hashChar(unsigned char c) {
    i++;
    j += S[i];
    swap(S[i], S[j]);
    return c ^ S[(unsigned char)(S[i] + S[j])];
}

/* Just set the key to the identity permutation. */
static void clearKey(void) {
    for(xChar = 0; xChar < KEY_LENGTH; xChar++) {
        S[xChar] = xChar;
    }
}

/* Throw away the first DISCARD_BYTES bytes, since they correlate to the key. */
static void throwAwaySomeBytes(int numBytes) {
    for(xChar = 0; xChar < numBytes; xChar++) {
        hashChar('\0');
    }
}

/* Initialize the key from the password, as done in ARC4. */
static void initKey(char *password, unsigned char *nonce, int nonceLength) {
    char *p = password;

    clearKey();
    j = 0;
    for(xChar = 0; xChar < KEY_LENGTH; xChar++) {
        if(*p == '\0') {
            p = password;
        }
        j += S[xChar] + *p++;
        swap(S[xChar], S[j]);
    }
    for(xChar = 0; xChar < nonceLength; xChar++) {
        j += S[i] + *nonce++;
        swap(S[i], S[j]);
        i++;
    }
    i = 0;
    j = 0;
}
