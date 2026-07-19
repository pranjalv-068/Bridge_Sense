import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';

function FatigueAnalytics({ fatigue }) {
  const actualPoints = fatigue.hours_actual.map((t, i) => ({
    t,
    actual: fatigue.actual_measurement[i],
    predictive: fatigue.predictive_maintenance_trend[i],
  }));

  const lastActualIdx = actualPoints.length - 1;
  const forecastPoints = fatigue.hours_forecast.map((t, i) => ({
    t,
    forecast: fatigue.forecast_projection[i],
  }));
  forecastPoints.unshift({ t: fatigue.hours_actual[lastActualIdx], forecast: fatigue.actual_measurement[lastActualIdx] });

  const combined = [
    ...actualPoints,
    ...forecastPoints.slice(1).map((p) => ({ t: p.t, forecast: p.forecast })),
  ];
  combined[lastActualIdx] = { ...combined[lastActualIdx], forecast: fatigue.actual_measurement[lastActualIdx] };

  return (
    <section className="panel section-block">
      <div className="section-head">
        <div>
          <div className="eyebrow">ANALYTICS &amp; FORECASTING</div>
          <h3 className="section-title" style={{ marginTop: 4 }}>24h Structural Fatigue Analysis</h3>
        </div>
        <div className="analytics-legend">
          <span><span className="legend-swatch" style={{ background: '#4fd8ff' }} />Actual measurement</span>
          <span><span className="legend-swatch" style={{ background: '#eab308' }} />Predictive maintenance trend</span>
          <span><span className="legend-swatch" style={{ background: '#ef4444' }} />NPU forecast projection</span>
        </div>
      </div>

      <div className="chart-wrap">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={combined} margin={{ top: 8, right: 16, left: -12, bottom: 0 }}>
            <CartesianGrid stroke="#1e2733" strokeDasharray="3 5" vertical={false} />
            <XAxis dataKey="t" tick={{ fill: '#57626f', fontSize: 10, fontFamily: 'JetBrains Mono' }} axisLine={{ stroke: '#1e2733' }} tickLine={false} />
            <YAxis tick={{ fill: '#57626f', fontSize: 10, fontFamily: 'JetBrains Mono' }} axisLine={false} tickLine={false} width={34} />
            <Tooltip
              contentStyle={{ background: '#10151f', border: '1px solid #1e2733', borderRadius: 8, fontFamily: 'JetBrains Mono', fontSize: 11 }}
              labelStyle={{ color: '#8a96a8' }}
            />
            <ReferenceLine x={fatigue.hours_actual[lastActualIdx]} stroke="#2a343f" strokeDasharray="2 3" />
            <Line type="monotone" dataKey="actual" name="Actual measurement" stroke="#4fd8ff" strokeWidth={2} dot={false} connectNulls />
            <Line type="monotone" dataKey="predictive" name="Predictive maintenance trend" stroke="#eab308" strokeWidth={1.6} strokeDasharray="4 3" dot={false} connectNulls />
            <Line type="monotone" dataKey="forecast" name="NPU forecast projection" stroke="#ef4444" strokeWidth={2} strokeDasharray="2 2" dot={false} connectNulls />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="fatigue-summary">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" style={{ flexShrink: 0, marginTop: 2 }}>
          <path d="M12 2 L2 21 H22 Z" stroke="#4fd8ff" strokeWidth="1.6" />
          <path d="M12 9v5" stroke="#4fd8ff" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        <span><b>NPU projection:</b> {fatigue.summary}</span>
      </div>
    </section>
  );
}

export default FatigueAnalytics;
