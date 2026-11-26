#include "../pk_wide_cache_view.h"

#include <stdio.h>
#include <stdlib.h>

static void expect(bool result, bool expected, const char* label) {
    if (result != expected) {
        fprintf(stderr, "%s: expected %d, got %d\n", label, expected, result);
        exit(EXIT_FAILURE);
    }
}

/** Visit the slot for each cell that actually contributes a visible pixel. */
static bool unique_slots(int worldOrigin, int extent) {
    bool visited[PK_WIDE_CACHE_SIZE / PK_WIDE_CACHE_CELL_SIZE] = { false };
    for (int i = 0; i < extent; ++i) {
        const unsigned address = (unsigned) (worldOrigin + i) % PK_WIDE_CACHE_SIZE;
        if (i != 0 && address % PK_WIDE_CACHE_CELL_SIZE != 0) continue;
        const unsigned slot = address / PK_WIDE_CACHE_CELL_SIZE;
        if (visited[slot]) return false;
        visited[slot] = true;
    }
    return true;
}

int main(void) {
    // Cache use can change around the same source size as a fractional camera origin crosses a cell edge.
    for (int origin = -9; origin <= 9; ++origin) {
        for (int extent = 504; extent <= 519; ++extent) {
            const struct PkWideView horizontal = { -140, -80, extent, 320 };
            const struct PkWideView vertical = { -140, -80, 440, extent };
            expect(pk_wide_cache_covers_view(horizontal, origin - horizontal.x, 0),
                   unique_slots(origin, extent), "horizontal cell coverage");
            expect(pk_wide_cache_covers_view(vertical, 0, origin - vertical.y),
                   unique_slots(origin, extent), "vertical cell coverage");
        }
    }

    const struct PkWideView wideHorizontal = { -200, -140, 640, 440 };
    const struct PkWideView wideVertical = { -100, -200, 440, 560 };
    const struct PkWideView docked = { -120, -100, 480, 360 };
    const struct PkWideView offLeft = { -401, 0, 400, 160 };
    const struct PkWideView offRight = { PK_WIDE_W, 0, 400, 160 };
    const struct PkWideView leftEdge = { -399, 0, 400, 160 };
    const struct PkWideView rightEdge = { PK_WIDE_W - 1, 0, 400, 160 };
    const struct PkWideView empty = { 0, 0, 0, 160 };
    expect(pk_wide_cache_covers_view(wideHorizontal, 0, 0), false, "wide horizontal view uses full renderer");
    expect(pk_wide_cache_covers_view(wideVertical, 0, 0), false, "wide vertical view uses full renderer");
    expect(pk_wide_cache_covers_view(docked, 0, 0), true, "resized docked view can use cache again");
    expect(pk_wide_cache_covers_view(offLeft, 0, 0), false, "left pan beyond original columns");
    expect(pk_wide_cache_covers_view(offRight, 0, 0), false, "right pan beyond original columns");
    expect(pk_wide_cache_covers_view(leftEdge, 0, 0), true, "one visible original column from the left");
    expect(pk_wide_cache_covers_view(rightEdge, 0, 0), true, "one visible original column from the right");
    expect(pk_wide_cache_covers_view(empty, 0, 0), false, "empty view");
    return EXIT_SUCCESS;
}
