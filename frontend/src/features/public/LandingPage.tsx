import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { PlanCard } from '../../components/PlanCard';
import { DisclaimerNote } from '../../components/DisclaimerNote';
import { useGetClassesQuery } from '../../api/classesApi';
import { useGetPlansQuery } from '../../api/membershipApi';
import { Dumbbell, Shield, Sparkles, Activity, CheckCircle2 } from 'lucide-react';

export const LandingPage: React.FC = () => {
  const { data: classesData, isLoading: classesLoading } = useGetClassesQuery({ size: 3 });
  const { data: plansData, isLoading: plansLoading } = useGetPlansQuery();

  return (
    <div className="min-h-screen flex flex-col bg-bg-page text-text-primary">
      {/* Navigation */}
      <header className="border-b border-border bg-bg-surface px-4 md:px-8 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2 text-brand-primary font-black text-xl">
          <div className="w-8 h-8 rounded-lg bg-brand-primary text-text-inverse flex items-center justify-center">
            <Dumbbell className="w-5 h-5" aria-hidden="true" />
          </div>
          <span>FitFlow</span>
        </Link>
        <div className="flex items-center gap-3">
          <Link to="/plans">
            <Button variant="ghost" size="sm">
              Plans
            </Button>
          </Link>
          <Link to="/login">
            <Button variant="outline" size="sm">
              Sign In
            </Button>
          </Link>
          <Link to="/register">
            <Button variant="primary" size="sm">
              Get Started
            </Button>
          </Link>
        </div>
      </header>

      {/* Hero */}
      <section className="py-16 md:py-24 px-4 text-center max-w-4xl mx-auto">
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-brand-primary/10 text-brand-primary mb-6">
          <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
          Intelligent Fitness & Studio Scheduling
        </span>
        <h1 className="text-4xl md:text-6xl font-black tracking-tight leading-tight mb-6">
          Move with purpose. <br />
          <span className="text-brand-primary">Train with intelligence.</span>
        </h1>
        <p className="text-base md:text-lg text-text-secondary max-w-2xl mx-auto mb-8">
          Personalized yoga, strength, cardio, and rehabilitation sessions booked effortlessly with real-time capacity and verified safety guidance.
        </p>
        <div className="flex flex-wrap items-center justify-center gap-4">
          <Link to="/register">
            <Button variant="primary" size="lg">
              Start Free Today
            </Button>
          </Link>
          <Link to="/plans">
            <Button variant="outline" size="lg">
              Explore Membership Tiers
            </Button>
          </Link>
        </div>
      </section>

      {/* Studio Value Pillars */}
      <section className="py-12 bg-bg-surface border-y border-border px-4">
        <div className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
          <Card className="p-6">
            <div className="w-10 h-10 rounded-lg bg-brand-primary/10 text-brand-primary flex items-center justify-center mb-4">
              <Activity className="w-5 h-5" aria-hidden="true" />
            </div>
            <h3 className="font-bold text-lg mb-2">Live Availability</h3>
            <p className="text-sm text-text-secondary">
              Instant spot booking, live waitlist queue positioning, and clear 2-hour cancellation windows.
            </p>
          </Card>
          <Card className="p-6">
            <div className="w-10 h-10 rounded-lg bg-purple-500/10 text-purple-600 flex items-center justify-center mb-4">
              <Shield className="w-5 h-5" aria-hidden="true" />
            </div>
            <h3 className="font-bold text-lg mb-2">Rule-Based Safety</h3>
            <p className="text-sm text-text-secondary">
              Deterministic contraindication screening and calorie estimations governed by strict wellness standards.
            </p>
          </Card>
          <Card className="p-6">
            <div className="w-10 h-10 rounded-lg bg-green-500/10 text-green-600 flex items-center justify-center mb-4">
              <CheckCircle2 className="w-5 h-5" aria-hidden="true" />
            </div>
            <h3 className="font-bold text-lg mb-2">Transparent Subscriptions</h3>
            <p className="text-sm text-text-secondary">
              Predictable monthly credits with exact prorated upgrades, downgrade refunds, and zero hidden fees.
            </p>
          </Card>
        </div>
      </section>

      {/* Featured Classes */}
      <section className="py-16 px-4 max-w-6xl mx-auto w-full">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold">Featured Classes</h2>
            <p className="text-sm text-text-secondary">Curated group fitness and mindfulness sessions</p>
          </div>
          <Link to="/register">
            <Button variant="link">View All Schedule &rarr;</Button>
          </Link>
        </div>

        {classesLoading ? (
          <p className="text-sm text-text-muted">Loading classes...</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {classesData?.content?.slice(0, 3).map((c) => (
              <Card key={c.id} className="p-5 flex flex-col justify-between">
                <div>
                  <span className="text-xs font-semibold text-brand-primary uppercase tracking-wider block mb-1">
                    {c.category} &bull; {c.intensity}
                  </span>
                  <h3 className="text-lg font-bold mb-2">{c.title}</h3>
                  <p className="text-xs text-text-secondary line-clamp-3 mb-4">{c.description}</p>
                </div>
                <div className="pt-4 border-t border-border flex items-center justify-between text-xs text-text-muted">
                  <span>{c.durationMinutes} mins</span>
                  <Link to="/register" className="text-brand-primary font-semibold hover:underline">
                    Book &rarr;
                  </Link>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* Plans Section */}
      <section className="py-16 px-4 bg-bg-surface border-t border-border">
        <div className="max-w-6xl mx-auto">
          <div className="text-center max-w-xl mx-auto mb-12">
            <h2 className="text-3xl font-bold mb-2">Simple, Predictable Plans</h2>
            <p className="text-sm text-text-secondary">
              Pick a membership that fits your routine. Upgrade, downgrade, or cancel at any time.
            </p>
          </div>

          {plansLoading ? (
            <p className="text-center text-sm text-text-muted">Loading plans...</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {plansData?.map((p) => (
                <PlanCard
                  key={p.id}
                  plan={p}
                  actionLabel="Join with this Plan"
                  onSelect={() => {
                    window.location.href = '/register';
                  }}
                />
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto border-t border-border bg-bg-surface px-4 py-8 text-center text-xs text-text-muted">
        <div className="max-w-4xl mx-auto space-y-4">
          <DisclaimerNote variant="compact" />
          <p>&copy; {new Date().getFullYear()} FitFlow Technologies Pvt. Ltd. DPDP Act Compliant Studio Management.</p>
        </div>
      </footer>
    </div>
  );
};
