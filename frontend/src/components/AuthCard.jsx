/** The centred card used by the signed-out pages (sign in, password reset). */
function AuthCard({ title, subtitle, children }) {
  return (
    <main className="signin-page">
      <section className="signin-card" aria-labelledby="auth-title">
        <div className="brand signin-brand">
          <span className="brand-mark">IQ</span>
          <div>
            <strong>IncidentIQ</strong>
            <small>Intelligent Operations</small>
          </div>
        </div>

        <h1 id="auth-title">{title}</h1>
        {subtitle && <p className="signin-subtitle">{subtitle}</p>}

        {children}
      </section>
    </main>
  )
}

export default AuthCard
