package com.munsun.logmasking.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Decorator around {@link ILoggingEvent} that overrides argument array
 * and formatted message with masked versions. All other properties
 * are delegated to the original event unchanged.
 */
final class MaskedLoggingEventDecorator implements ILoggingEvent {

    private final ILoggingEvent delegate;
    private final Object[] maskedArgs;
    private String cachedFormattedMessage;

    MaskedLoggingEventDecorator(ILoggingEvent delegate, Object[] maskedArgs) {
        this.delegate = delegate;
        this.maskedArgs = maskedArgs;
    }

    @Override
    public String getFormattedMessage() {
        if (cachedFormattedMessage == null) {
            if (maskedArgs != null && maskedArgs.length > 0) {
                cachedFormattedMessage = MessageFormatter
                        .arrayFormat(delegate.getMessage(), maskedArgs)
                        .getMessage();
            } else {
                cachedFormattedMessage = delegate.getMessage();
            }
        }
        return cachedFormattedMessage;
    }

    @Override
    public Object[] getArgumentArray() {
        return maskedArgs;
    }

    // ---- pure delegation --------------------------------------------------

    @Override public String getThreadName()               { return delegate.getThreadName(); }
    @Override public Level getLevel()                      { return delegate.getLevel(); }
    @Override public String getMessage()                   { return delegate.getMessage(); }
    @Override public String getLoggerName()                { return delegate.getLoggerName(); }
    @Override public LoggerContextVO getLoggerContextVO()  { return delegate.getLoggerContextVO(); }
    @Override public IThrowableProxy getThrowableProxy()   { return delegate.getThrowableProxy(); }
    @Override public StackTraceElement[] getCallerData()   { return delegate.getCallerData(); }
    @Override public boolean hasCallerData()               { return delegate.hasCallerData(); }
    @Override public List<Marker> getMarkerList()          { return delegate.getMarkerList(); }
    @Override public Map<String, String> getMDCPropertyMap(){ return delegate.getMDCPropertyMap(); }
    @SuppressWarnings("deprecation")
    @Override public Map<String, String> getMdc()             { return delegate.getMdc(); }
    @Override public long getTimeStamp()                   { return delegate.getTimeStamp(); }
    @Override public int getNanoseconds()                  { return delegate.getNanoseconds(); }
    @Override public long getSequenceNumber()              { return delegate.getSequenceNumber(); }
    @Override public List<KeyValuePair> getKeyValuePairs() { return delegate.getKeyValuePairs(); }
    @Override public Instant getInstant()                  { return delegate.getInstant(); }
    @Override public void prepareForDeferredProcessing()   { delegate.prepareForDeferredProcessing(); }
}
