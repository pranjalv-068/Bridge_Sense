function batteryColor(pct) {
  if (pct < 25) return 'var(--red)';
  if (pct < 50) return 'var(--yellow)';
  return 'var(--green)';
}

function NodeDeploymentStatus({ nodes, npuByNode }) {
  return (
    <section className="panel section-block">
      <div className="section-head">
        <h3 className="section-title">Node Deployment Status</h3>
        <button className="recalibrate-btn">
          RECALIBRATE CLUSTER
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
            <path d="M4 12a8 8 0 0 1 14-5.3M20 12a8 8 0 0 1-14 5.3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            <path d="M18 4v4h-4M6 20v-4h4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <div className="node-grid">
        {nodes.map((n) => {
          const npu = npuByNode[n.id];
          return (
            <div key={n.id} className={`node-card ${n.status}`}>
              <div className="node-card-head">
                <span className="node-card-id">{n.id}</span>
                <span className={`status-dot ${n.status} ${n.status !== 'good' ? 'pulse' : ''}`} />
              </div>
              <div className="node-card-label">{n.label}</div>

              <div className="node-stat-row">
                <span>Battery</span>
                <span className="val">{n.battery_pct}%</span>
              </div>
              <div className="battery-track">
                <div className="battery-fill" style={{ width: `${n.battery_pct}%`, background: batteryColor(n.battery_pct) }} />
              </div>

              <div className="node-stat-row">
                <span>Signal</span>
                <span className="val dim">{n.signal_dbm} dBm</span>
              </div>
              <div className="node-stat-row">
                <span>Sample rate</span>
                <span className="val dim">{n.sampling_freq_hz} Hz</span>
              </div>

              <div className="node-status-line">
                <span className={`status-dot ${n.status}`} />
                {n.operating ? 'OPERATING AS INTENDED' : 'OFFLINE'}
                {npu && npu.status !== 'good' && (
                  <span style={{ marginLeft: 'auto', color: npu.status === 'critical' ? 'var(--red)' : 'var(--yellow)' }}>
                    {(npu.anomaly_score * 100).toFixed(0)}%
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

export default NodeDeploymentStatus;
