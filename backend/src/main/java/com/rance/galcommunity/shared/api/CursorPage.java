package com.rance.galcommunity.shared.api;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }
}
