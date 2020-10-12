#pragma once

// Runs the given cleanup function at scope exit. This includes returns and exceptions.
// Usage: `ScopedCleanup someCleanup([res] { cleanup(res); });`
// It is recommended never to touch the cleanup object after declaring it.
template<typename F>
class ScopedCleanup {
    F _f;

public:
    [[nodiscard]] ScopedCleanup(F f) : _f(f) {}
    ~ScopedCleanup() {
        _f();
    }

    // As this is a simple utility, we don't support copy or move semantics.
    ScopedCleanup(const ScopedCleanup&) = delete;
    ScopedCleanup(ScopedCleanup&&) = delete;

    ScopedCleanup& operator=(const ScopedCleanup&) = delete;
    ScopedCleanup& operator=(ScopedCleanup&&) = delete;
};