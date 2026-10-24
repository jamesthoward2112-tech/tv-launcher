package uk.co.howardtv;

import android.animation.TimeInterpolator;

/**
 * Step response of a slightly underdamped spring, normalised to [0, 1]: a quick move that
 * overshoots by a few percent and settles. Used as an interpolator so a plain ValueAnimator
 * gives spring motion without pulling in a physics library.
 */
final class Spring implements TimeInterpolator {
    static final Spring INSTANCE = new Spring(0.72f, 10f);

    private final float zeta, omega, omegaD;

    Spring(float damping, float stiffness) {
        zeta = damping;
        omega = stiffness;
        omegaD = (float) (omega * Math.sqrt(1 - zeta * zeta));
    }

    @Override
    public float getInterpolation(float t) {
        if (t >= 1f) return 1f;
        double decay = Math.exp(-zeta * omega * t);
        return (float) (1 - decay * (Math.cos(omegaD * t) + zeta * omega / omegaD * Math.sin(omegaD * t)));
    }
}
