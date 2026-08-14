/* Diario nutrizionale — frontend senza build step.
   Servito da Spring da src/main/resources/static.               */

'use strict';

const $ = (id) => document.getElementById(id);

/* ---------------------------------------------------------------
   Stato
   --------------------------------------------------------------- */
const stato = {
  email: null,
  nutrienti: [],       // catalogo dal backend: ordine, unità, gerarchia
  alimenti: [],
  scelto: null,
  grammi: '100',
  pastoTarget: null,
  giorno: oggiISO(),
  diario: null,
  modificando: null,   // id alimento in modifica, null se nuovo
  fonteProposta: 'MANUALE',
  pastoAperto: null,   // id pasto aperto nel popup, null se chiuso
};

/* ---------------------------------------------------------------
   Utilità
   --------------------------------------------------------------- */
function oggiISO(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** L'utente scrive 1,6 — il JSON vuole 1.6. Conversione solo al bordo. */
const num = (v) => {
  if (v === null || v === undefined || String(v).trim() === '') return null;
  const n = parseFloat(String(v).replace(',', '.'));
  return isNaN(n) ? null : n;
};

const fmt = (v, unita) => {
  if (v === null || v === undefined) return '—';
  if (unita === 'kcal') return String(Math.round(v));
  const d = unita === 'mg' || unita === 'ug' ? (v > 0 && v < 10 ? 2 : 0) : 1;
  return v.toFixed(d).replace('.', ',');
};

const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function dataLunga(iso) {
  const [a, m, g] = iso.split('-').map(Number);
  const d = new Date(a, m - 1, g);
  if (iso === oggiISO()) return 'Oggi';
  return d.toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric', month: 'long' });
}

function pastoSuggerito() {
  const h = new Date().getHours();
  if (h < 11) return 'COLAZIONE';
  if (h < 16) return 'PRANZO';
  return 'CENA';
}

const nomePasto = (p) => p.nome || ({
  COLAZIONE: 'Colazione', PRANZO: 'Pranzo', CENA: 'Cena', SPUNTINO: 'Spuntino',
}[p.tipo] || p.tipo);

/* ---------------------------------------------------------------
   Rete
   --------------------------------------------------------------- */
function csrf() {
  const c = document.cookie.split('; ').find((x) => x.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=')[1]) : null;
}

async function api(metodo, percorso, corpo) {
  const testate = { 'Content-Type': 'application/json' };
  const t = csrf();
  if (t && metodo !== 'GET') testate['X-XSRF-TOKEN'] = t;

  const r = await fetch(percorso, {
    method: metodo,
    headers: testate,
    credentials: 'same-origin',
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });

  if (r.status === 401) { mostraAccesso(); throw new Error('non autenticato'); }
  if (r.status === 204) return null;

  const testo = await r.text();
  const dati = testo ? JSON.parse(testo) : null;
  if (!r.ok) throw new Error(dati?.detail || dati?.title || 'richiesta non riuscita');
  return dati;
}

/* ---------------------------------------------------------------
   Accesso
   --------------------------------------------------------------- */
let modoAccesso = 'accesso';

function mostraAccesso() {
  $('schermata-app').hidden = true;
  $('schermata-accesso').hidden = false;
}

document.querySelectorAll('[data-modo]').forEach((b) => {
  b.addEventListener('click', () => {
    modoAccesso = b.dataset.modo;
    document.querySelectorAll('[data-modo]').forEach((x) => x.classList.toggle('seg--att', x === b));
    $('campo-invito').hidden = modoAccesso !== 'registrazione';
    $('btn-entra').textContent = modoAccesso === 'registrazione' ? 'Crea account' : 'Entra';
    $('in-password').autocomplete = modoAccesso === 'registrazione' ? 'new-password' : 'current-password';
    $('accesso-errore').hidden = true;
  });
});

$('btn-entra').addEventListener('click', async () => {
  const err = $('accesso-errore');
  err.hidden = true;
  const email = $('in-email').value.trim();
  const password = $('in-password').value;

  if (!email || !password) {
    err.textContent = 'Servono email e password.';
    err.hidden = false;
    return;
  }

  try {
    if (modoAccesso === 'registrazione') {
      await api('POST', '/api/auth/registrazione',
        { email, password, invito: $('in-invito').value.trim() });
    } else {
      await api('POST', '/api/auth/accesso', { email, password });
    }
    $('in-password').value = '';
    await avvia();
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  }
});

[$('in-email'), $('in-password'), $('in-invito')].forEach((el) =>
  el.addEventListener('keydown', (e) => { if (e.key === 'Enter') $('btn-entra').click(); }));

$('btn-esci').addEventListener('click', async () => {
  try { await api('POST', '/api/auth/uscita'); } catch (e) { /* la sessione è già andata */ }
  location.reload();
});

/* ---------------------------------------------------------------
   Etichetta nutrizionale
   --------------------------------------------------------------- */
function etichetta(titolo, sottotitolo, valori, totale) {
  const righe = stato.nutrienti.map((n, i) => {
    const precedente = stato.nutrienti[i - 1];
    const stacco = n.unita === 'mg' && precedente && precedente.unita !== 'mg';
    const v = valori ? valori[n.codice] : null;
    const assente = v === null || v === undefined;
    return `<tr class="${stacco ? 'stacco' : ''}">
      <th class="${n.padre ? 'sub' : ''}">
        <span class="th-in">
          <svg class="n-ic n-ic--${n.codice}" aria-hidden="true"><use href="#ic-${n.codice}"></use></svg>
          <span>${esc(n.nome)}</span>
        </span>
      </th>
      <td>
        <span class="val ${assente ? 'val--assente' : ''}">${fmt(assente ? null : v, n.unita)}</span>
        <span class="unit">${assente ? '' : esc(n.unita)}</span>
      </td></tr>`;
  }).join('');

  return `<div class="etichetta ${totale ? 'etichetta--totale' : ''}">
    <div class="etichetta-testa">
      <h3>${esc(titolo)}</h3>
      ${sottotitolo ? `<span class="etichetta-per">${esc(sottotitolo)}</span>` : ''}
    </div>
    <div class="barra"></div>
    <table class="tab"><tbody>${righe}</tbody></table>
  </div>`;
}

/* ---------------------------------------------------------------
   Dispensa
   --------------------------------------------------------------- */
async function caricaAlimenti(q) {
  const url = '/api/alimenti' + (q ? `?q=${encodeURIComponent(q)}` : '');
  stato.alimenti = await api('GET', url);
  disegnaDispensa();
}

function disegnaDispensa() {
  $('conta-alimenti').textContent = stato.alimenti.length;
  const ul = $('lista-alimenti');

  if (stato.alimenti.length === 0) {
    ul.innerHTML = `<li><p class="vuoto">Nessun ingrediente. Creane uno copiando i valori dall'etichetta.</p></li>`;
    return;
  }

  ul.innerHTML = stato.alimenti.map((a) => `
    <li class="${stato.scelto?.id === a.id ? 'att' : ''}">
      <button class="lista-scelta" data-scegli="${a.id}">
        <span class="lista-nome">${esc(a.nome)}${a.marca ? ` <span class="lista-marca">${esc(a.marca)}</span>` : ''}</span>
        <span class="lista-kcal">${fmt(a.kcal, 'kcal')}<em>kcal/100g</em></span>
      </button>
      <button class="mini" data-modifica="${a.id}">modifica</button>
      <button class="mini mini--rosso" data-elimina="${a.id}">archivia</button>
    </li>`).join('');
}

$('lista-alimenti').addEventListener('click', async (e) => {
  const b = e.target.closest('button');
  if (!b) return;

  if (b.dataset.scegli) {
    const completo = await api('GET', `/api/alimenti/${b.dataset.scegli}`);
    stato.scelto = completo;
    stato.grammi = '100';
    disegnaDispensa();
    disegnaBilancia();
    $('in-grammi').focus();
  }
  if (b.dataset.modifica) {
    const completo = await api('GET', `/api/alimenti/${b.dataset.modifica}`);
    apriModale(completo);
  }
  if (b.dataset.elimina) {
    if (!confirm('Archiviare questo ingrediente? Le voci di diario già registrate restano.')) return;
    await api('DELETE', `/api/alimenti/${b.dataset.elimina}`);
    if (stato.scelto?.id === Number(b.dataset.elimina)) { stato.scelto = null; disegnaBilancia(); }
    await caricaAlimenti($('in-cerca').value.trim());
  }
});

let attesaRicerca;
$('in-cerca').addEventListener('input', (e) => {
  clearTimeout(attesaRicerca);
  const q = e.target.value.trim();
  attesaRicerca = setTimeout(() => caricaAlimenti(q), 220);
});

/* ---------------------------------------------------------------
   Bilancia
   --------------------------------------------------------------- */
function proporziona(per100, grammi) {
  const g = num(grammi);
  if (g === null) return null;
  const out = {};
  Object.entries(per100 || {}).forEach(([k, v]) => {
    if (v !== null && v !== undefined) out[k] = v * g / 100;
  });
  return out;
}

function disegnaBilancia() {
  const pannello = $('pannello-bilancia');
  const attivo = !!stato.scelto;

  pannello.classList.toggle('bilancia--spenta', !attivo);
  $('btn-cambia').hidden = !attivo;
  $('bilancia-nome').textContent = attivo
    ? stato.scelto.nome + (stato.scelto.stato !== 'CRUDO' ? ` · ${stato.scelto.stato.toLowerCase()}` : '')
    : 'Scegli un ingrediente dalla dispensa';

  ['in-grammi', 'btn-meno', 'btn-piu'].forEach((id) => { $(id).disabled = !attivo; });
  document.querySelectorAll('.chip').forEach((c) => { c.disabled = !attivo; });

  $('lcd-grammi').textContent = attivo ? fmt(num(stato.grammi), 'g') : '—';
  $('in-grammi').value = stato.grammi;

  if (!attivo) {
    $('anteprima').innerHTML = '';
    $('destinazione').hidden = true;
    return;
  }

  const valori = proporziona(stato.scelto.per100, stato.grammi);
  $('anteprima').innerHTML = etichetta(
    stato.scelto.nome, `per ${fmt(num(stato.grammi), 'g')} g`, valori, false);
  $('destinazione').hidden = false;
  disegnaSegmentiPasto();
}

function disegnaSegmentiPasto() {
  const pasti = stato.diario?.pasti || [];
  if (!pasti.some((p) => p.id === stato.pastoTarget)) {
    stato.pastoTarget = (pasti.find((p) => p.tipo === pastoSuggerito()) || pasti[0])?.id ?? null;
  }
  $('segmenti-pasto').innerHTML = pasti.map((p) =>
    `<button class="seg ${p.id === stato.pastoTarget ? 'seg--att' : ''}" data-pasto="${p.id}">${esc(nomePasto(p))}</button>`
  ).join('');

  const scelto = pasti.find((p) => p.id === stato.pastoTarget);
  $('btn-aggiungi').textContent = scelto ? `Aggiungi a ${nomePasto(scelto).toLowerCase()}` : 'Aggiungi al pasto';
}

$('segmenti-pasto').addEventListener('click', (e) => {
  const b = e.target.closest('[data-pasto]');
  if (!b) return;
  stato.pastoTarget = Number(b.dataset.pasto);
  disegnaSegmentiPasto();
});

$('btn-cambia').addEventListener('click', () => { stato.scelto = null; disegnaDispensa(); disegnaBilancia(); });
$('in-grammi').addEventListener('input', (e) => { stato.grammi = e.target.value; disegnaBilancia(); });
$('btn-meno').addEventListener('click', () => { stato.grammi = String(Math.max(0, (num(stato.grammi) || 0) - 10)); disegnaBilancia(); });
$('btn-piu').addEventListener('click', () => { stato.grammi = String((num(stato.grammi) || 0) + 10); disegnaBilancia(); });
$('in-grammi').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('btn-aggiungi').click(); });

$('chip-grammi').innerHTML = [20, 30, 50, 80, 100, 150, 200]
  .map((g) => `<button class="chip" data-g="${g}" disabled>${g}</button>`).join('');
$('chip-grammi').addEventListener('click', (e) => {
  const b = e.target.closest('[data-g]');
  if (!b) return;
  stato.grammi = b.dataset.g;
  disegnaBilancia();
});

$('btn-aggiungi').addEventListener('click', async () => {
  const g = num(stato.grammi);
  if (!stato.scelto || !stato.pastoTarget || g === null || g <= 0) return;
  await api('POST', `/api/pasti/${stato.pastoTarget}/voci`,
    { alimentoId: stato.scelto.id, grammi: g });
  stato.scelto = null;
  stato.grammi = '100';
  disegnaDispensa();
  disegnaBilancia();
  await caricaDiario();
});

/* ---------------------------------------------------------------
   Diario
   --------------------------------------------------------------- */
async function caricaDiario() {
  stato.diario = await api('GET', `/api/giorni/${stato.giorno}`);
  disegnaDiario();
  disegnaSegmentiPasto();
  if (stato.pastoAperto != null) disegnaPopupPasto();
}

function disegnaDiario() {
  const d = stato.diario;
  $('titolo-giorno').textContent = dataLunga(stato.giorno);
  $('kcal-giorno').textContent = fmt(d.totale?.kcal ?? 0, 'kcal');

  const nVoci = d.pasti.reduce((s, p) => s + p.voci.length, 0);

  const carte = d.pasti.map((p) => `
    <button class="pasto-card" data-pasto-apri="${p.id}">
      <span class="pc-testa">
        <span class="pc-nome">${esc(nomePasto(p))}</span>
        <svg class="pc-freccia" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6l6 6-6 6"></path></svg>
      </span>
      <span class="pc-kcal">${fmt(p.totale?.kcal ?? 0, 'kcal')}<em>kcal</em></span>
      <span class="pc-prot">Proteine ${fmt(p.totale?.proteine ?? 0, 'g')} g</span>
    </button>`).join('');

  $('colonna-diario').innerHTML = `
    <div class="pasti-testa"><span class="eyebrow">Pasti di oggi</span></div>
    <div class="pasti-griglia">${carte}</div>
    <button class="btn btn--largo" id="btn-spuntino">+ Aggiungi spuntino</button>
    <div class="scheda scheda--totale">
      ${etichetta('Totale giornata', `${nVoci} ${nVoci === 1 ? 'voce' : 'voci'} in ${d.pasti.length} pasti`, d.totale, true)}
    </div>`;
}

/* ---------------------------------------------------------------
   Popup dettaglio pasto
   --------------------------------------------------------------- */
function apriPasto(id) {
  stato.pastoAperto = id;
  disegnaPopupPasto();
  $('velo-pasto').hidden = false;
}

function chiudiPasto() { $('velo-pasto').hidden = true; stato.pastoAperto = null; }

function disegnaPopupPasto() {
  const p = stato.diario?.pasti.find((x) => x.id === stato.pastoAperto);
  if (!p) { chiudiPasto(); return; }
  $('modale-pasto-titolo').textContent = nomePasto(p);

  const voci = p.voci.length === 0
    ? `<p class="vuoto">Niente qui. Pesa un ingrediente sulla bilancia e aggiungilo a questo pasto.</p>`
    : `<ul class="voci">${p.voci.map((v) => `
        <li>
          <span class="voce-nome">${esc(v.nome)}</span>
          <span class="voce-peso">
            <input inputmode="decimal" value="${fmt(v.grammi, 'g')}" data-voce="${v.id}" aria-label="Grammi di ${esc(v.nome)}">
            <em>g</em>
          </span>
          <span class="voce-kcal">${fmt(v.valori?.kcal ?? 0, 'kcal')}</span>
          <button class="x x--piccolo" data-togli="${v.id}" aria-label="Togli dal pasto">×</button>
        </li>`).join('')}</ul>`;

  $('modale-pasto-corpo').innerHTML = voci
    + (p.voci.length ? etichetta(nomePasto(p), 'totale del pasto', p.totale, false) : '');
}

$('colonna-diario').addEventListener('click', async (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  if (b.id === 'btn-spuntino') {
    await api('POST', `/api/giorni/${stato.giorno}/pasti`, { tipo: 'SPUNTINO' });
    await caricaDiario();
    return;
  }
  if (b.dataset.pastoApri) apriPasto(Number(b.dataset.pastoApri));
});

$('btn-chiudi-pasto').addEventListener('click', chiudiPasto);
$('velo-pasto').addEventListener('click', (e) => { if (e.target === $('velo-pasto')) chiudiPasto(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('velo-pasto').hidden) chiudiPasto(); });

$('modale-pasto-corpo').addEventListener('click', async (e) => {
  const b = e.target.closest('button');
  if (!b || !b.dataset.togli) return;
  await api('DELETE', `/api/voci/${b.dataset.togli}`);
  await caricaDiario();
});

$('modale-pasto-corpo').addEventListener('change', async (e) => {
  const i = e.target.closest('[data-voce]');
  if (!i) return;
  const g = num(i.value);
  if (g === null || g <= 0) { await caricaDiario(); return; }
  await api('PATCH', `/api/voci/${i.dataset.voce}`, { grammi: g });
  await caricaDiario();
});

/* ---------------------------------------------------------------
   Navigazione fra i giorni
   --------------------------------------------------------------- */
function spostaGiorno(delta) {
  const [a, m, g] = stato.giorno.split('-').map(Number);
  const d = new Date(a, m - 1, g + delta);
  stato.giorno = oggiISO(d);
  $('in-data').value = stato.giorno;
  caricaDiario();
}
$('btn-ieri').addEventListener('click', () => spostaGiorno(-1));
$('btn-domani').addEventListener('click', () => spostaGiorno(1));
$('in-data').addEventListener('change', (e) => {
  stato.giorno = e.target.value || oggiISO();
  caricaDiario();
});

/* ---------------------------------------------------------------
   Modale ingrediente
   --------------------------------------------------------------- */
function disegnaCampiNutrienti(valori) {
  $('campi-nutrienti').innerHTML = stato.nutrienti.map((n) => {
    const v = valori?.[n.codice];
    const testo = v === null || v === undefined ? '' : String(v).replace('.', ',');
    return `<label class="campo-nutriente ${n.padre ? 'campo-nutriente--sub' : ''}">
      <span class="cn-nome">
        <svg class="n-ic n-ic--${n.codice}" aria-hidden="true"><use href="#ic-${n.codice}"></use></svg>
        ${esc(n.nome)}
      </span>
      <span class="campo-input">
        <input inputmode="decimal" data-n="${n.codice}" value="${esc(testo)}" placeholder="—">
        <em>${esc(n.unita)}</em>
      </span>
    </label>`;
  }).join('');
}

function apriModale(alimento) {
  stato.modificando = alimento?.id ?? null;
  $('modale-titolo').textContent = alimento ? 'Modifica ingrediente' : 'Nuovo ingrediente';
  $('btn-salva').textContent = alimento ? 'Salva modifiche' : 'Salva in dispensa';
  $('f-nome').value = alimento?.nome ?? '';
  $('f-marca').value = alimento?.marca ?? '';
  $('f-stato').value = alimento?.stato ?? 'CRUDO';
  $('f-sale').value = '';
  $('modale-errore').hidden = true;
  $('modale-avviso').hidden = true;
  disegnaCampiNutrienti(alimento?.per100);
  stato.fonteProposta = alimento?.fonte || 'MANUALE';
  $('f-cerca-off').value = '';
  $('risultati-off').innerHTML = '';
  statoOff(null);
  $('velo').hidden = false;
  $('f-nome').focus();
}

function chiudiModale() { $('velo').hidden = true; stato.modificando = null; }

$('btn-nuovo').addEventListener('click', () => apriModale(null));
$('btn-chiudi').addEventListener('click', chiudiModale);
$('btn-annulla').addEventListener('click', chiudiModale);
$('velo').addEventListener('click', (e) => { if (e.target === $('velo')) chiudiModale(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('velo').hidden) chiudiModale(); });

/* Il sale in etichetta vale 400 mg di sodio per grammo. */
$('f-sale').addEventListener('input', (e) => {
  const s = num(e.target.value);
  const campo = document.querySelector('[data-n="sodio"]');
  if (campo && s !== null) campo.value = String(Math.round(s * 400));
});

$('btn-salva').addEventListener('click', async () => {
  const err = $('modale-errore');
  const avv = $('modale-avviso');
  err.hidden = true; avv.hidden = true;

  const nome = $('f-nome').value.trim();
  if (!nome) { err.textContent = 'Serve un nome per ritrovarlo nella dispensa.'; err.hidden = false; return; }

  // Campo vuoto significa "non dichiarato", non zero: non finisce nel corpo.
  const per100 = {};
  document.querySelectorAll('[data-n]').forEach((i) => {
    const v = num(i.value);
    if (v !== null) per100[i.dataset.n] = v;
  });

  const corpo = {
    nome,
    marca: $('f-marca').value.trim() || null,
    stato: $('f-stato').value,
    fonte: stato.fonteProposta || 'MANUALE',
    per100,
  };

  try {
    const r = stato.modificando
      ? await api('PUT', `/api/alimenti/${stato.modificando}`, corpo)
      : await api('POST', '/api/alimenti', corpo);

    if (r?.avviso) {
      avv.textContent = r.avviso + ' — salvato comunque, controlla i valori.';
      avv.hidden = false;
      setTimeout(chiudiModale, 2600);
    } else {
      chiudiModale();
    }
    await caricaAlimenti($('in-cerca').value.trim());
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  }
});


/* ---------------------------------------------------------------
   Ricerca su Open Food Facts
   --------------------------------------------------------------- */

/** Campi che ci si aspetta su un'etichetta europea. */
const NECESSARI = ['kcal', 'grassi', 'saturi', 'carbo', 'zuccheri', 'proteine', 'sodio'];

const memoria = { risultatiOff: [] };

/** Un codice a barre è 8, 12 o 13 cifre e nient'altro. */
const sembraEan = (q) => /^\d{8}$|^\d{12,13}$/.test(q.replace(/\s/g, ''));

function statoOff(testo, tipo) {
  const el = $('stato-off');
  if (!testo) { el.hidden = true; return; }
  el.textContent = testo;
  el.className = 'avviso' + (tipo ? ' avviso--' + tipo : '');
  el.hidden = false;
}

async function cercaOff() {
  const q = $('f-cerca-off').value.trim();
  if (q.length < 2) {
    statoOff('Scrivi almeno due caratteri, o un codice a barre.', 'attenzione');
    return;
  }

  const bottone = $('btn-cerca-off');
  bottone.disabled = true;
  bottone.textContent = 'Cerco…';
  $('risultati-off').innerHTML = '';
  statoOff(null);

  try {
    if (sembraEan(q)) {
      // Codice a barre: un solo risultato, lo applico subito.
      const p = await api('GET', `/api/lookup/ean/${encodeURIComponent(q)}`);
      applicaProdotto(p);
      return;
    }
    const trovati = await api('GET', `/api/lookup/nome?q=${encodeURIComponent(q)}`);
    if (!trovati.length) {
      statoOff('Nessun prodotto trovato. Prova con un nome diverso, o compila i campi a mano.', 'attenzione');
      return;
    }
    statoOff(`${trovati.length} risultat${trovati.length === 1 ? 'o' : 'i'}. Scegline uno per compilare i campi.`);
    disegnaRisultatiOff(trovati);
  } catch (e) {
    console.error('ricerca Open Food Facts:', e);
    statoOff('Ricerca non riuscita: ' + e.message, 'male');
  } finally {
    bottone.disabled = false;
    bottone.textContent = 'Cerca';
  }
}

function disegnaRisultatiOff(trovati) {
  memoria.risultatiOff = trovati;
  $('risultati-off').innerHTML = trovati.map((p, i) => {
    const kcal = p.per100?.kcal;
    const mancanti = NECESSARI.filter((k) => p.per100?.[k] === undefined);
    return `<li>
      <button class="risultato" data-i="${i}">
        <span class="risultato-nome">${esc(p.nome)}</span>
        <span class="risultato-info">
          ${p.marca ? esc(p.marca) + ' · ' : ''}
          <span class="risultato-kcal">${kcal === undefined ? '—' : fmt(kcal, 'kcal') + ' kcal'}</span>
          ${mancanti.length ? ` · <span class="risultato-parziale">manca ${mancanti.length} valore${mancanti.length > 1 ? 'i' : ''}</span>` : ''}
        </span>
      </button>
    </li>`;
  }).join('');
}

function applicaProdotto(p) {
  stato.fonteProposta = p.fonte || 'MANUALE';
  if (p.nome) $('f-nome').value = p.nome;
  if (p.marca) $('f-marca').value = p.marca;

  // Riempie solo i campi che la fonte dichiara: vuoto resta vuoto,
  // perché "non dichiarato" non è "zero".
  document.querySelectorAll('[data-n]').forEach((i) => {
    const v = p.per100?.[i.dataset.n];
    if (v !== undefined && v !== null) i.value = String(v).replace('.', ',');
  });

  $('risultati-off').innerHTML = '';
  $('f-cerca-off').value = '';

  const mancanti = NECESSARI.filter((k) => p.per100?.[k] === undefined);
  statoOff(mancanti.length
    ? 'Valori dal produttore. La scheda è incompleta: controlla i campi rimasti vuoti sulla confezione.'
    : 'Valori dal produttore. Controllali con l\'etichetta prima di salvare: le ricette cambiano nel tempo.',
    mancanti.length ? 'attenzione' : null);
}

$('btn-cerca-off').addEventListener('click', cercaOff);
$('f-cerca-off').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); cercaOff(); }
});

$('risultati-off').addEventListener('click', (e) => {
  const b = e.target.closest('[data-i]');
  if (!b) return;
  applicaProdotto(memoria.risultatiOff[Number(b.dataset.i)]);
});

/* ---------------------------------------------------------------
   Avvio
   --------------------------------------------------------------- */
async function avvia() {
  const io = await api('GET', '/api/auth/io');
  stato.email = io.email;
  $('etichetta-utente').textContent = io.email;

  $('schermata-accesso').hidden = true;
  $('schermata-app').hidden = false;

  stato.nutrienti = await api('GET', '/api/nutrienti');
  $('in-data').value = stato.giorno;

  await caricaAlimenti();
  await caricaDiario();
  disegnaBilancia();
}

avvia().catch(() => mostraAccesso());
