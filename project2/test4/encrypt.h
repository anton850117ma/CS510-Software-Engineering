/* Note that you need to #include "encrypt.c" rather than "encrypt.h" -- this allows a bit better
 * optimization. */

#define KEY_LENGTH 256
#define NONCE_LENGTH 20
#define DISCARD_BYTES 1024
#define DIFFICULTY 200000 /* We throw away DISCARD_BYTES*DIFFICULTY bytes */

static void initKey(char *password, unsigned char *nonce, int nonceLength);
static void throwAwaySomeBytes(int numBytes);
static void clearKey(void);
static unsigned char hashChar(unsigned char c);
