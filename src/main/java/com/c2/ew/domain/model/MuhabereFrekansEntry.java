package com.c2.ew.domain.model;

import com.c2.ew.domain.enums.MuhabereFrekansTipi;

public record MuhabereFrekansEntry(
    MuhabereFrekansTipi frekansTipi,
    Double minFrekansMhz,
    Double maxFrekansMhz
) {}
