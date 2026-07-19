import React from 'react';

function AIExplanation({ nodes, npuByNode, activeNodeId, setActiveNodeId }) {
  const activeNode = nodes.find(n => n.id === activeNodeId);
  const npuRecord = npuByNode[activeNodeId] || {
    severity_level: 'Normal',
    anomaly_score: 0.05,
    confidence: 0.95,
    forecast_trend: 'Stable',
    forecast_eta: null,
    explanation: 'System is running normally.'
  };

  const severity = npuRecord.severity_level || (activeNode?.status === 'critical' ? 'Critical' : activeNode?.status === 'warning' ? 'Minor' : 'Normal');
  const score = npuRecord.anomaly_score || 0.0;
  const confidence = npuRecord.confidence || 0.95;
  const trend = npuRecord.forecast_trend || 'Stable';
  const eta = npuRecord.forecast_eta;
  const explanation = npuRecord.explanation || npuRecord.summary || 'Telemetry aligns with normal baseline.';

  let severityClass = 'normal';
  let severityLabel = 'NOMINAL';
  let statusColor = 'var(--green)';
  let glowColor = 'rgba(34,197,94,0.15)';

  if (severity === 'Critical') {
    severityClass = 'critical';
    severityLabel = 'CRITICAL';
    statusColor = 'var(--red)';
    glowColor = 'rgba(239,68,68,0.2)';
  } else if (severity === 'Major') {
    severityClass = 'major';
    severityLabel = 'MAJOR ANOMALY';
    statusColor = '#f97316';
    glowColor = 'rgba(249,115,22,0.15)';
  } else if (severity === 'Minor') {
    severityClass = 'minor';
    severityLabel = 'MINOR DRIFT';
    statusColor = 'var(--yellow)';
    glowColor = 'rgba(234,179,8,0.15)';
  }

  return (
    <section className="panel section-block ai-explainer-panel" style={{
      background: `linear-gradient(180deg, var(--panel), ${glowColor})`,
      border: `1px solid ${severity === 'Normal' ? 'var(--panel-border)' : severity === 'Minor' ? 'rgba(234,179,8,0.3)' : severity === 'Major' ? 'rgba(249,115,22,0.3)' : 'rgba(239,68,68,0.45)'}`,
      transition: 'all 0.3s ease'
    }}>
      <div className="section-head" style={{ borderBottom: '1px solid var(--panel-border)', paddingBottom: '12px', marginBottom: '16px' }}>
        <div>
          <div className="eyebrow" style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--cyan)' }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
            </svg>
            AI PC MAIN BRAIN
          </div>
          <h3 className="section-title" style={{ marginTop: '4px' }}>NPU Diagnostic &amp; LLM Explainability</h3>
        </div>
        <div className="node-selector-tabs" style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          {nodes.map(n => (
            <button
              key={n.id}
              onClick={() => setActiveNodeId(n.id)}
              className={`tab-btn ${n.id === activeNodeId ? 'active' : ''} ${n.status}`}
              style={{
                fontFamily: 'var(--mono)',
                fontSize: '10.5px',
                padding: '5px 10px',
                borderRadius: '6px',
                border: n.id === activeNodeId ? `1px solid ${statusColor}` : '1px solid var(--panel-border)',
                background: n.id === activeNodeId ? 'rgba(13,18,25,0.8)' : '#0d1219',
                color: n.id === activeNodeId ? 'var(--text-hi)' : 'var(--text-mid)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              <span className={`status-dot ${n.status}`} style={{ width: '6px', height: '6px' }} />
              {n.id.replace('NODE_', 'N')}
            </button>
          ))}
        </div>
      </div>

      <div className="ai-explainer-body" style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px' }}>
        <div className="ai-diag-card" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div className="diag-header-row" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div className={`severity-badge ${severityClass}`} style={{
              padding: '10px 18px',
              borderRadius: '8px',
              border: `1px solid ${statusColor}`,
              background: 'rgba(12,17,26,0.9)',
              display: 'flex',
              flexDirection: 'column',
              minWidth: '130px',
              alignItems: 'center'
            }}>
              <span style={{ fontSize: '9px', fontFamily: 'var(--mono)', color: 'var(--text-dim)', letterSpacing: '0.05em' }}>SEVERITY LEVEL</span>
              <span style={{ fontSize: '15px', fontWeight: 'bold', color: statusColor, marginTop: '2px', letterSpacing: '0.02em' }}>{severityLabel}</span>
            </div>
            
            <div className="diag-metrics" style={{ flexGrow: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div className="metric-box" style={{ background: '#0d1219', padding: '8px 12px', borderRadius: '6px', border: '1px solid var(--panel-border)' }}>
                <div style={{ fontSize: '9.5px', color: 'var(--text-dim)', fontFamily: 'var(--mono)' }}>RECONSTRUCTION MSE</div>
                <div style={{ fontSize: '15px', fontWeight: '600', color: 'var(--text-hi)', fontFamily: 'var(--mono)', marginTop: '2px' }}>{score.toFixed(3)}</div>
              </div>
              <div className="metric-box" style={{ background: '#0d1219', padding: '8px 12px', borderRadius: '6px', border: '1px solid var(--panel-border)' }}>
                <div style={{ fontSize: '9.5px', color: 'var(--text-dim)', fontFamily: 'var(--mono)' }}>DIAGNOSTIC CONFIDENCE</div>
                <div style={{ fontSize: '15px', fontWeight: '600', color: 'var(--text-hi)', fontFamily: 'var(--mono)', marginTop: '2px' }}>{(confidence * 100).toFixed(1)}%</div>
              </div>
            </div>
          </div>

          <div className="llm-bubble" style={{
            background: '#090d14',
            border: '1px solid var(--panel-border)',
            borderRadius: '8px',
            padding: '14px',
            position: 'relative',
            boxShadow: 'inset 0 0 10px rgba(0,0,0,0.5)'
          }}>
            <div className="bubble-label" style={{
              fontSize: '8.5px',
              fontFamily: 'var(--mono)',
              color: 'var(--cyan)',
              letterSpacing: '0.08em',
              borderBottom: '1px dashed var(--panel-border)',
              paddingBottom: '6px',
              marginBottom: '8px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
              <span>LLM NATURAL LANGUAGE EXPLANATION</span>
              <span style={{ color: 'var(--text-dim)' }}>GROUNDED AI INFERENCE</span>
            </div>
            <p className="bubble-content" style={{
              fontSize: '12.5px',
              lineHeight: '1.5',
              color: 'var(--text-hi)',
              margin: 0,
              fontFamily: 'system-ui, -apple-system, sans-serif'
            }}>
              "{explanation}"
            </p>
          </div>
        </div>

        <div className="ai-forecast-card" style={{
          background: '#0d1219',
          border: '1px solid var(--panel-border)',
          borderRadius: '8px',
          padding: '14px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}>
          <div>
            <div style={{ fontSize: '10px', fontFamily: 'var(--mono)', color: 'var(--text-dim)', letterSpacing: '0.04em', marginBottom: '12px' }}>
              ANOMALY FORECAST ENGINE
            </div>
            
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingBottom: '10px', borderBottom: '1px dashed var(--panel-border)', marginBottom: '10px' }}>
              <span style={{ fontSize: '12px', color: 'var(--text-mid)' }}>Historical Trend</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{
                  fontSize: '12px',
                  fontWeight: '600',
                  color: trend === 'Increasing' ? 'var(--red)' : trend === 'Decreasing' ? 'var(--green)' : 'var(--text-mid)'
                }}>
                  {trend.toUpperCase()}
                </span>
                <span style={{ fontSize: '14px' }}>
                  {trend === 'Increasing' ? '📈' : trend === 'Decreasing' ? '📉' : '➡️'}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '12px', color: 'var(--text-mid)' }}>ETA to Critical Threshold</span>
              <div style={{ textAlign: 'right' }}>
                {eta !== null && eta !== undefined ? (
                  <div>
                    <span style={{ fontSize: '18px', fontWeight: 'bold', color: 'var(--red)', fontFamily: 'var(--mono)' }}>
                      {eta.toFixed(1)}
                    </span>
                    <span style={{ fontSize: '10.5px', color: 'var(--text-dim)', fontFamily: 'var(--mono)', marginLeft: '4px' }}>
                      HOURS
                    </span>
                  </div>
                ) : (
                  <span style={{ fontSize: '12px', color: 'var(--text-dim)', fontStyle: 'italic' }}>
                    Stable (No ETA)
                  </span>
                )}
              </div>
            </div>
          </div>

          <div style={{
            background: 'rgba(79,216,255,0.03)',
            border: '1px dashed rgba(79,216,255,0.15)',
            borderRadius: '6px',
            padding: '8px 10px',
            fontSize: '10.5px',
            color: 'var(--text-dim)',
            lineHeight: '1.4',
            marginTop: '12px',
            display: 'flex',
            gap: '8px',
            alignItems: 'center'
          }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" strokeWidth="2" style={{ flexShrink: 0 }}>
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="16" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
            <span>
              {eta !== null && eta !== undefined 
                ? 'Regression trend shows anomaly error path reaching 0.880 threshold. Schedule inspection.' 
                : 'Sensor signals match trained baseline. Continuing real-time tracking.'}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}

export default AIExplanation;
