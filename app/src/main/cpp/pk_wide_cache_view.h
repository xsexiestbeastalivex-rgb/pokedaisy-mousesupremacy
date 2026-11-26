#pragma once

#include "pk_wide.h"

#define PK_WIDE_CACHE_SIZE 512
#define PK_WIDE_CACHE_CELL_SIZE 8

/**
 * A slot stores one entire world cell. Even a pixel span no longer than the
 * ring can alias its first cell when its last edge reaches one extra cell.
 * The cached row compositor also requires an intersection with the original
 * frame's columns. The full renderer handles either case at the requested size.
 */
static inline bool pk_wide_cache_covers_view(struct PkWideView view, int worldX, int worldY) {
    const int xPhase = ((unsigned) worldX + (unsigned) view.x) & (PK_WIDE_CACHE_CELL_SIZE - 1);
    const int yPhase = ((unsigned) worldY + (unsigned) view.y) & (PK_WIDE_CACHE_CELL_SIZE - 1);
    return view.w > 0 && view.h > 0 && view.x < PK_WIDE_W && view.x > -view.w &&
           view.w <= PK_WIDE_CACHE_SIZE - xPhase && view.h <= PK_WIDE_CACHE_SIZE - yPhase;
}
