package com.software.sello

/**
 * A device test, or a class of them, that runs in the nightly and manual runs only,
 * never on a pull request: a long corpus, a sweep of more windows, a large history.
 * A pull request still covers the behaviour in a shorter form.
 *
 * `scripts/check --device` leaves these out with the instrumentation argument
 * `notAnnotation=com.software.sello.NightlyOnly` unless it is given `--scope nightly`
 * (scripts/quality/gates.py); the Quality workflow passes `--scope nightly` on the
 * schedule and on a manual run. Moving or renaming this class needs the same change there.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class NightlyOnly
