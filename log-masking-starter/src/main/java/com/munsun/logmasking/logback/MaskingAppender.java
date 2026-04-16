package com.munsun.logmasking.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.munsun.logmasking.core.FieldMaskingService;

/**
 * Logback appender that wraps an existing appender and replaces arguments
 * containing {@code @Masked} fields with masked proxy objects before
 * forwarding the event. The original appender's configuration (layout,
 * encoder, filters) is completely preserved.
 */
public class MaskingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    static final String NAME_PREFIX = "masking:";

    private final Appender<ILoggingEvent> delegate;
    private final FieldMaskingService maskingService;

    public MaskingAppender(Appender<ILoggingEvent> delegate, FieldMaskingService maskingService) {
        this.delegate = delegate;
        this.maskingService = maskingService;
        setName(NAME_PREFIX + delegate.getName());
    }

    @Override
    protected void append(ILoggingEvent event) {
        delegate.doAppend(maskEventIfNeeded(event));
    }

    @Override
    public void stop() {
        super.stop();
        delegate.stop();
    }

    Appender<ILoggingEvent> getDelegate() {
        return delegate;
    }

    private ILoggingEvent maskEventIfNeeded(ILoggingEvent event) {
        Object[] args = event.getArgumentArray();
        if (args == null || args.length == 0) {
            return event;
        }

        boolean needsMasking = false;
        Object[] maskedArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && maskingService.hasMaskedFields(arg.getClass())) {
                maskedArgs[i] = new MaskedObjectWrapper(arg, maskingService);
                needsMasking = true;
            } else {
                maskedArgs[i] = arg;
            }
        }

        if (!needsMasking) {
            return event;
        }
        return new MaskedLoggingEventDecorator(event, maskedArgs);
    }
}
