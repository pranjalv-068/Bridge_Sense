import { LineChart, Line, ResponsiveContainer, YAxis } from 'recharts';

function toSeries(values, timestamps) {
  return values.map((v, i) => ({ t: timestamps[i], v }));
}

function MiniChart({ data, color }) {
  return (
    <ResponsiveContainer width="100%" height={64}>
      <LineChart data={data} margin={{ top: 4, right: 4, left: 4, bottom: 0 }}>
        <YAxis hide domain={['dataMin - 1', 'dataMax + 1']} />
        <Line type="monotone" dataKey="v" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}

function TelemetryStream({ cluster }) {
  const vib = toSeries(cluster.vibration_hz, cluster.timestamps);
  const tilt = toSeries(cluster.tilt_deg, cluster.timestamps);
  const strain = toSeries(cluster.strain_ue, cluster.timestamps);

  const strainHigh = cluster.strain_avg_current > 300;

  return (
    <section className="panel">
      <div className="panel-head">
        <div className="eyebrow">TELEMETRY STREAM</div>
        <h3 className="panel-title">Active Sensor Cluster</h3>
      </div>

      <div className="telemetry-list">
        <div className="telemetry-card">
          <div className="telemetry-card-head">
            <span className="telemetry-label">〜 VIBRATION (Hz)</span>
            <div className="telemetry-value-group">
              <span className="telemetry-value" style={{ color: 'var(--cyan)' }}>{cluster.vibration_avg_current}</span>
              <span className="telemetry-unit">avg</span>
            </div>
          </div>
          <MiniChart data={vib} color="#4fd8ff" />
        </div>

        <div className="telemetry-card">
          <div className="telemetry-card-head">
            <span className="telemetry-label">∧ TILT (DEGREES)</span>
            <div className="telemetry-value-group">
              <span className="telemetry-value" style={{ color: 'var(--green)' }}>{cluster.tilt_avg_current}°</span>
              <span className="telemetry-unit">stable</span>
            </div>
          </div>
          <MiniChart data={tilt} color="#22c55e" />
        </div>

        <div className="telemetry-card">
          <div className="telemetry-card-head">
            <span className="telemetry-label">▤ STRAIN (µε)</span>
            <div className="telemetry-value-group">
              <span className="telemetry-value" style={{ color: strainHigh ? 'var(--red)' : 'var(--text-hi)' }}>
                {cluster.strain_avg_current}
              </span>
              <span className="telemetry-unit">{strainHigh ? 'high' : 'nominal'}</span>
            </div>
          </div>
          <MiniChart data={strain} color={strainHigh ? '#ef4444' : '#8a96a8'} />
        </div>
      </div>
    </section>
  );
}

export default TelemetryStream;
