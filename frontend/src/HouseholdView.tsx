import { useState } from "react";
import { Copy, MessageCircle, Users } from "lucide-react";
import { householdApi, type Household } from "./api";

export function HouseholdView({ households, refresh }: { households: Household[]; refresh: () => void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [generated, setGenerated] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const household = households[0];

  const fail = (cause: unknown) => setError(cause instanceof Error ? cause.message : "No se pudo completar la operación");
  const copy = async () => {
    await navigator.clipboard.writeText(generated);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };
  const share = async () => {
    const text = `Únete a mi unidad doméstica en ANURA con este código: ${generated}`;
    if (navigator.share) {
      try { await navigator.share({ title: "Invitación ANURA", text }); return; } catch { /* WhatsApp fallback */ }
    }
    window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, "_blank", "noopener,noreferrer");
  };
  const invitation = generated ? (
    <div className="invite-code">
      <small>CÓDIGO TEMPORAL · CADUCA {expiresAt ? new Date(expiresAt).toLocaleString("es") : "EN 48 HORAS"}</small>
      <strong>{generated}</strong>
      {copied && <span className="success">Código copiado</span>}
      <div className="invite-actions">
        <button type="button" onClick={() => void copy()}><Copy />Copiar código</button>
        <button type="button" onClick={() => void share()}><MessageCircle />Compartir</button>
      </div>
    </div>
  ) : null;

  if (household) return (
    <div className="household-view">
      <div className="household-card"><Users /><div><b>{household.name}</b><small>Tu rol: {household.role}</small></div></div>
      {household.role === "OWNER" && <>
        <label>Email opcional para restringir el código<input value={email} onChange={event => setEmail(event.target.value)} type="email" placeholder="persona@email.com" /></label>
        <p className="form-note">Si no indicas email, cualquier usuario autenticado podrá usar el código. Si el correo está configurado, ANURA también enviará la invitación.</p>
        <button className="primary" disabled={busy} onClick={async () => {
          setBusy(true); setError("");
          try {
            const result = await householdApi.invite(household.id, email);
            setGenerated(result.code); setExpiresAt(result.expiresAt);
            setMessage(result.deliveryStatus === "SENT" ? "Invitación enviada y lista para compartir." : result.recipientStatus === "NEW_USER" ? "Ese email aún no tiene cuenta. Comparte también el enlace de ANURA." : result.recipientStatus === "REGISTERED_USER" ? "Usuario encontrado; código restringido a su email." : "Código listo para compartir.");
          } catch (cause) { fail(cause); } finally { setBusy(false); }
        }}>{busy ? "Generando…" : generated ? "Generar otro código" : email ? "Crear invitación" : "Generar código"}</button>
        {invitation}{message && <p className="form-note">{message}</p>}
      </>}
      {error && <div className="error" role="alert">{error}</div>}
    </div>
  );

  return (
    <div className="household-view">
      <label>Nombre de la unidad<input value={name} maxLength={160} onChange={event => setName(event.target.value)} placeholder="Ej. Casa José y Mónica" /></label>
      <button className="primary" disabled={busy || !name.trim()} onClick={async () => {
        if (busy) return; setBusy(true); setError("");
        try {
          const result = await householdApi.create(name);
          setGenerated(result.invitation.code); setExpiresAt(result.invitation.expiresAt);
          setMessage(`${result.household.name} creada correctamente.`); refresh();
        } catch (cause) { fail(cause); } finally { setBusy(false); }
      }}>{busy ? "Creando…" : "Crear unidad doméstica"}</button>
      {invitation}{message && <p className="success">{message}</p>}
      <div className="or">o aceptar invitación</div>
      <label>Código<input value={code} onChange={event => setCode(event.target.value.toUpperCase())} placeholder="ANURA-XXXX-XXXX" /></label>
      <button className="primary secondary" disabled={busy || !code.trim()} onClick={async () => {
        if (busy) return; setBusy(true); setError("");
        try { await householdApi.accept(code); refresh(); } catch (cause) { fail(cause); } finally { setBusy(false); }
      }}>{busy ? "Comprobando…" : "Unirme"}</button>
      {error && <div className="error" role="alert">{error}</div>}
    </div>
  );
}
