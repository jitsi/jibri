/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.jibri.util

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

/**
 * Run [block] inside a new span named [name], making it the current span for the duration of [block].  The span
 * is marked as an error (and the exception recorded on it) if [block] throws, and is always ended before this
 * function returns or rethrows.
 */
fun <T> Tracer.withSpan(
    name: String,
    parent: Context = Context.current(),
    kind: SpanKind = SpanKind.INTERNAL,
    configure: SpanBuilder.() -> Unit = {},
    block: () -> T
): T {
    val span = spanBuilder(name)
        .setParent(parent)
        .setSpanKind(kind)
        .apply(configure)
        .startSpan()
    return try {
        span.makeCurrent().use { block() }
    } catch (t: Throwable) {
        span.recordException(t)
        span.setStatus(StatusCode.ERROR, t.message ?: "")
        throw t
    } finally {
        span.end()
    }
}

private val traceparentPattern = Regex("^[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")

/**
 * Extracts a remote [Context] from a W3C trace context `traceparent` header value
 * (https://www.w3.org/TR/trace-context/#traceparent-header), or returns the root context if the value is absent
 * or malformed.
 */
fun remoteContextFromTraceparent(traceparent: String?): Context {
    val root = Context.root()
    val match = traceparent?.let { traceparentPattern.matchEntire(it) } ?: return root
    val (traceId, spanId, traceFlags) = match.destructured
    val spanContext = SpanContext.createFromRemoteParent(
        traceId,
        spanId,
        TraceFlags.fromHex(traceFlags, 0),
        TraceState.getDefault()
    )
    if (!spanContext.isValid) {
        return root
    }
    return root.with(Span.wrap(spanContext))
}
