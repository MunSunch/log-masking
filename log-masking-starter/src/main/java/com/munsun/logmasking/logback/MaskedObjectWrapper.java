package com.munsun.logmasking.logback;

import com.munsun.logmasking.core.FieldMaskingService;

/**
 * Lightweight wrapper whose {@link #toString()} returns the masked
 * representation of the delegate object. The original object is never mutated.
 */
public final class MaskedObjectWrapper {

    private final Object delegate;
    private final FieldMaskingService service;
    private String cached;

    public MaskedObjectWrapper(Object delegate, FieldMaskingService service) {
        this.delegate = delegate;
        this.service = service;
    }

    @Override
    public String toString() {
        if (cached == null) {
            cached = service.toMaskedString(delegate);
        }
        return cached;
    }
}
