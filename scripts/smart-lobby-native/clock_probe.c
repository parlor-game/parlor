#define _POSIX_C_SOURCE 200809L

#include <inttypes.h>
#include <stdio.h>
#include <time.h>

int main(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_REALTIME, &now) != 0) {
        return 1;
    }
    const int64_t millis = (int64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
    return printf("%" PRId64 "\n", millis) < 0;
}
