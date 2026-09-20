/*
 * Rizqflow — prototipe klik (S05, S06/S07, S11, S16, S21, S24-S28 + tab lain versi ringkas).
 * Ini prototipe untuk menilai tampilan dan alur, BUKAN kode produksi.
 * Semua data adalah contoh. Uang selalu bilangan bulat (rupiah), tidak pernah floating point.
 */
(function () {
  'use strict';

  /* ------------------------------------------------------------------ util */

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const NBSP = ' ';
  const nf = new Intl.NumberFormat('id-ID');
  const rp = (n) => (n < 0 ? '−' : '') + 'Rp' + NBSP + nf.format(Math.abs(Math.round(n)));
  const rpShort = (n) => {
    if (n >= 1e6) {
      return 'Rp' + NBSP + (n / 1e6).toLocaleString('id-ID', { maximumFractionDigits: 2 }) + NBSP + 'jt';
    }
    if (n >= 1e3) return 'Rp' + NBSP + nf.format(Math.round(n / 1e3)) + NBSP + 'rb';
    return 'Rp' + NBSP + nf.format(n);
  };
  const esc = (s) =>
    String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const pct = (ratio) => Math.round(ratio * 100);

  /* ----------------------------------------------------------------- ikon */

  const ICON = {
    heart: '<path d="M12 20.5s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.6a4.2 4.2 0 0 1 7.5 2.6c0 5.7-7.5 10.3-7.5 10.3z"/>',
    sprout:
      '<path d="M12 21v-9"/><path d="M12 13c0-4.2-3-6.5-7-6.5 0 4.2 3 6.5 7 6.5z"/><path d="M12 11c0-3.4 2.4-5.4 6-5.4 0 3.4-2.4 5.4-6 5.4z"/>',
    home: '<path d="M3.5 11.2 12 3.8l8.5 7.4"/><path d="M5.5 10v10h13V10"/><path d="M10 20v-5.5h4V20"/>',
    check: '<circle cx="12" cy="12" r="9"/><path d="m8 12.4 2.8 2.8 5.4-5.6"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7.2V12l3.2 2"/>',
    alert: '<path d="M12 3.6 21.4 20H2.6z"/><path d="M12 10v4.6"/><path d="M12 17.4h.01"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    back: '<path d="m15 5-7 7 7 7"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    star: '<path d="m12 3.5 2.5 5.2 5.7.8-4.1 4 1 5.7L12 16.5l-5.1 2.7 1-5.7-4.1-4 5.7-.8z"/>',
    backspace: '<path d="M9 5h10a1.5 1.5 0 0 1 1.5 1.5v11A1.5 1.5 0 0 1 19 19H9l-6-7z"/><path d="m12.5 9.5 5 5m0-5-5 5"/>',
    income: '<path d="M12 19V6"/><path d="m6.5 11.5 5.5-5.5 5.5 5.5"/>',
    bolt: '<path d="M13 3 5 13.5h6L10 21l8-10.5h-6z"/>',
    bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    balance:
      '<path d="M12 4v16M6 20h12"/><path d="M5 8h14"/><path d="m5 8-2.5 6a3 3 0 0 0 5 0z"/><path d="m19 8 2.5 6a3 3 0 0 1-5 0z"/>',
    inbox: '<path d="M4 13.5 6.5 5h11L20 13.5V19H4z"/><path d="M4 13.5h5l1 2h4l1-2h5"/>',
  };
  const icon = (name) => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>`;

  /* ----------------------------------------------------------------- data */

  const ORDER = ['memberi', 'diri', 'keluarga'];
  const ACCT_ORDER = ['tunai', 'jago', 'gopay'];

  const seed = () => ({
    pending: [{ id: 'p1', label: 'Freelance desain', amount: 1200000 }],
    rooms: {
      memberi: {
        id: 'memberi', name: 'Memberi', slot: 1, icon: 'heart', kind: 'menunaikan', kindLabel: 'Menunaikan',
        verb: 'Tersalur', pct: 10, alloc: 850000, used: 300000,
        pos: [{ name: 'Sedekah', base: 400000, used: 100000 }, { name: 'Infak', base: 450000, used: 200000 }],
      },
      diri: {
        id: 'diri', name: 'Diri', slot: 2, icon: 'sprout', kind: 'menumbuhkan', kindLabel: 'Menumbuhkan',
        verb: 'Terinvestasi', pct: 30, alloc: 2550000, used: 2550000,
        pos: [
          { name: 'Dana darurat', base: 1020000, used: 1020000 },
          { name: 'Investasi', base: 1148000, used: 1148000 },
          { name: 'Belajar', base: 382000, used: 382000 },
        ],
      },
      keluarga: {
        id: 'keluarga', name: 'Keluarga', slot: 3, icon: 'home', kind: 'mencukupi', kindLabel: 'Mencukupi',
        verb: 'Terpakai', pct: 60, alloc: 5100000, used: 4335000,
        pos: [
          { name: 'Belanja bulanan', base: 2000000, used: 1600000 },
          { name: 'Listrik dan air', base: 1000000, used: 550000 },
          { name: 'Sekolah', base: 1200000, used: 1200000 },
          { name: 'Lain-lain', base: 900000, used: 985000 },
        ],
      },
    },
    tx: [
      { date: '18 Sep', title: 'Belanja pasar', sub: 'Belanja bulanan · Keluarga', amt: -150000, room: 'keluarga', acct: 'jago' },
      { date: '17 Sep', title: 'Token listrik', sub: 'Listrik dan air · Keluarga', amt: -200000, room: 'keluarga', acct: 'jago' },
      { date: '15 Sep', title: 'Infak masjid', sub: 'Infak · Memberi', amt: -200000, room: 'memberi', acct: 'tunai' },
      { date: '12 Sep', title: 'Reksa dana pasar uang', sub: 'Investasi · Diri', amt: -1148000, room: 'diri', acct: 'jago' },
      { date: '12 Sep', title: 'Dana darurat', sub: 'Dana darurat · Diri', amt: -1020000, room: 'diri', acct: 'jago' },
      { date: '8 Sep', title: 'Sedekah', sub: 'Sedekah · Memberi', amt: -100000, room: 'memberi', acct: 'tunai' },
      { date: '1 Sep', title: 'Gaji September', sub: 'Pemasukan · dialirkan ke 3 ruang', amt: 8500000, room: null, acct: 'jago' },
    ],
    // Akun: "days" = hari sejak terakhir dicocokkan (Koreksi saldo, S25).
    acct: {
      tunai: { id: 'tunai', name: 'Tunai', bal: 312000, days: 3 },
      jago: { id: 'jago', name: 'Bank Jago', bal: 6840000, days: 2 },
      gopay: { id: 'gopay', name: 'GoPay', bal: 185000, days: 12 },
    },
    favs: [
      { id: 'f1', name: 'Kopi', amt: 15000, room: 'keluarga', cat: 'Lain-lain', acct: 'tunai' },
      { id: 'f2', name: 'Parkir', amt: 3000, room: 'keluarga', cat: 'Lain-lain', acct: 'tunai' },
      { id: 'f3', name: 'Jajan', amt: 20000, room: 'keluarga', cat: 'Lain-lain', acct: 'tunai' },
      { id: 'f4', name: 'Bensin', amt: 30000, room: 'keluarga', cat: 'Lain-lain', acct: 'gopay' },
    ],
    checked: { '19 Sep': false, '20 Sep': false },
    pro: false,
    capture: { perm: false, apps: { gopay: true, jago: true } },
    drafts: [],
    sampleIdx: 0,
    reminder: { on: true, hour: '21.00' },
    last: { room: 'keluarga', cat: 'Lain-lain' },
    lastAcct: 'tunai',
  });

  let S = seed();
  let prevView = null; // snapshot Denah sebelum alokasi, untuk animasi aliran
  let route = { name: 'denah' };
  let stack = [];
  let draft = newDraft();
  let A = null; // draf alokasi (S07)
  let undo = null;
  let toastTimer = null;
  let K = null; // draf Catat kilat (S24)
  let K2 = null; // draf Koreksi saldo (S25)
  let sheetAfter = null; // aksi yang dilanjutkan setelah pembelian (flow F6)

  function newDraft() {
    return { mode: 'masuk', amount: '', sumber: 'Freelance', room: 'keluarga', cat: null, acct: 'jago', fav: false, fromDraft: null };
  }

  /* ------------------------------------------------------------- turunan */

  const rooms = () => ORDER.map((id) => S.rooms[id]);
  const pendingTotal = () => S.pending.reduce((a, p) => a + p.amount, 0);
  const allocTotal = () => rooms().reduce((a, r) => a + r.alloc, 0);
  const incomeTotal = () => allocTotal() + pendingTotal();
  const color = (r) => `var(--rf-room-${r.slot})`;
  const accts = () => ACCT_ORDER.map((id) => S.acct[id]);
  const catsOf = (r) => r.pos.filter((p) => p.base > 0);

  /** Ruang dari pengeluaran terakhir di sebuah akun; cadangan: ruang bertipe Mencukupi (Keluarga). */
  function lastRoomFor(acctId) {
    const t = S.tx.find((x) => x.acct === acctId && x.amt < 0 && x.room);
    return t ? t.room : 'keluarga';
  }

  /** Satu pengeluaran memengaruhi ruang, pos, saldo akun, dan daftar transaksi sekaligus. */
  function addExpense({ n, room, cat, acct, title }) {
    const r = S.rooms[room];
    r.used += n;
    let p = r.pos.find((x) => x.name === cat);
    if (!p) {
      p = { name: cat, base: 0, used: 0 };
      r.pos.push(p);
    }
    p.used += n;
    S.acct[acct].bal -= n;
    S.tx.unshift({ date: 'Hari ini', title: title || cat, sub: `${cat} · ${r.name}`, amt: -n, room: r.id, acct });
  }

  /** Status hak terpenuhi menurut tipe ruang (dikonfirmasi 2026-09-20, lihat docs/konsep.md). */
  function status(r) {
    const ratio = r.alloc ? r.used / r.alloc : 0;
    if (r.kind === 'mencukupi') {
      return ratio >= 0.85
        ? { key: 'attn', label: 'Perlu perhatian', ratio }
        : { key: 'progress', label: 'Berjalan', ratio };
    }
    return ratio >= 1
      ? { key: 'good', label: 'Terpenuhi', ratio }
      : { key: 'progress', label: 'Berjalan', ratio };
  }

  const STATUS_ICON = { good: 'check', progress: 'clock', attn: 'alert' };
  const chip = (st) =>
    `<span class="chip chip--${st.key}">${icon(STATUS_ICON[st.key])}${st.label}</span>`;

  function posList(r) {
    const counted = r.pos.filter((p) => p.base > 0);
    const total = counted.reduce((a, p) => a + p.base, 0);
    let left = r.alloc;
    return r.pos.map((p) => {
      if (!p.base) return { name: p.name, alloc: 0, used: p.used };
      const last = p === counted[counted.length - 1];
      const a = last ? left : Math.round((r.alloc * p.base) / total / 1000) * 1000;
      left -= a;
      return { name: p.name, alloc: a, used: p.used };
    });
  }

  function snapshot() {
    return {
      rooms: Object.fromEntries(rooms().map((r) => [r.id, { ratio: Math.min(1, r.alloc ? r.used / r.alloc : 0), alloc: r.alloc }])),
    };
  }

  /** Membagi nominal ke ruang. Sisa pembulatan masuk ke ruang pertama (prioritas). */
  function split(amount, pcts) {
    const total = ORDER.reduce((a, id) => a + pcts[id], 0);
    const parts = ORDER.map((id) => Math.floor((amount * pcts[id]) / 100));
    const valid = total <= 100;
    let rest = 0;
    if (valid) {
      const target = Math.floor((amount * total) / 100);
      parts[0] += target - parts.reduce((a, b) => a + b, 0);
      rest = amount - target;
    }
    return { parts: Object.fromEntries(ORDER.map((id, i) => [id, parts[i]])), total, rest, valid };
  }

  /* ----------------------------------------------------------- komponen */

  /** Cincin dibatasi 100%, tetapi angka di tengah memakai rasio sebenarnya (mis. 110%). */
  function ring(fromRatio, toRatio, r, text, extra = '', realRatio = toRatio) {
    const label = `${pct(realRatio)}%`;
    return `<div class="ring ${extra}" style="--seg:${color(r)}" role="img" aria-label="${esc(r.verb)} ${label}">
      <svg viewBox="0 0 36 36" aria-hidden="true">
        <circle class="ring__track" cx="18" cy="18" r="15.5"/>
        <circle class="ring__val" cx="18" cy="18" r="15.5" pathLength="100" transform="rotate(-90 18 18)"
          stroke-dasharray="${(fromRatio * 100).toFixed(2)} 100" data-to-dash="${(toRatio * 100).toFixed(2)}"/>
      </svg>
      <span class="ring__text">${text === undefined ? label : text}</span>
    </div>`;
  }

  function stackBar(items, restAmt, ariaLabel) {
    const segs = items
      .map(
        (i) =>
          `<span class="seg" style="--seg:${i.color};flex-grow:${(i.from / 1000).toFixed(3)}" data-to-grow="${(i.to / 1000).toFixed(3)}"></span>`
      )
      .join('');
    const rest = restAmt > 0 ? `<span class="seg seg--rest" style="flex-grow:${(restAmt / 1000).toFixed(3)}"></span>` : '';
    return `<div class="stack" role="img" aria-label="${esc(ariaLabel)}">${segs}${rest}</div>`;
  }

  function legend(items, total, restAmt) {
    const li = items
      .map(
        (i) =>
          `<li><span class="dot" style="--seg:${i.color}"></span>${esc(i.name)} ${total ? pct(i.to / total) : 0}%</li>`
      )
      .join('');
    const rest = restAmt > 0 ? `<li><span class="dot dot--rest"></span>Belum dialirkan ${pct(restAmt / total)}%</li>` : '';
    return `<ul class="legend">${li}${rest}</ul>`;
  }

  const roomIcon = (r) => `<span class="room-icon" style="--seg:${color(r)}">${icon(r.icon)}</span>`;

  const topbarNav = (title) =>
    `<header class="topbar topbar--nav"><button type="button" class="icon-btn" data-action="back" aria-label="Kembali">${icon('back')}</button><h1 tabindex="-1">${title}</h1></header>`;

  /* -------------------------------------------------------------- layar */

  function denahScreen() {
    const prev = prevView;
    prevView = null;
    const total = incomeTotal();
    const pend = pendingTotal();
    const items = rooms().map((r) => ({
      id: r.id, name: r.name, color: color(r), to: r.alloc, from: prev ? prev.rooms[r.id].alloc : r.alloc,
    }));
    const aria =
      'Pembagian rezeki: ' + items.map((i) => `${i.name} ${total ? pct(i.to / total) : 0}%`).join(', ') +
      (pend ? `, belum dialirkan ${pct(pend / total)}%` : '');

    const cards = rooms()
      .map((r) => {
        const st = status(r);
        const to = Math.min(1, st.ratio);
        const from = prev ? prev.rooms[r.id].ratio : to;
        const flowing = route.flow && route.flow.includes(r.id) ? ' is-flowing' : '';
        return `<button type="button" class="room-card${flowing}" style="--seg:${color(r)}" data-action="open-room" data-arg="${r.id}">
          <span class="room-card__head">${roomIcon(r)}<span class="room-name">${esc(r.name)}</span></span>
          <span class="room-card__body">${ring(from, to, r, undefined, '', st.ratio)}
            <span class="metric"><b>${rpShort(r.used)}</b>${r.verb} dari ${rpShort(r.alloc)}</span></span>
          ${chip(st)}
        </button>`;
      })
      .join('');

    const attn = [];
    if (pend) {
      attn.push({ icon: 'alert', tone: '', text: `${rp(pend)} belum dialirkan`, action: ['Alirkan', 'alirkan-pending'] });
    }
    rooms().forEach((r) => {
      const st = status(r);
      if (st.key === 'attn') {
        attn.push({ icon: 'alert', tone: '', text: `${r.name} hampir melewati jatah (${pct(st.ratio)}%)`, action: ['Lihat', 'open-room', r.id] });
      }
    });
    accts().forEach((a) => {
      if (a.days > 7) {
        attn.push({ icon: 'clock', tone: 'info', text: `Saldo ${a.name} belum dicocokkan ${a.days} hari`, action: ['Cocokkan', 'open-koreksi', a.id] });
      }
    });
    attn.push({ icon: 'clock', tone: 'info', text: 'Nilai harta zakat terakhir diperbarui 40 hari lalu', action: ['Lihat', 'open-haul'] });
    const attnHtml = attn
      .slice(0, 3)
      .map(
        (a) => `<li><div class="list-row"><span class="attn-icon ${a.tone ? 'attn-icon--info' : ''}">${icon(a.icon)}</span>
        <span class="list-row__main">${esc(a.text)}</span>
        <button type="button" class="btn btn--text btn--small" data-action="${a.action[1]}" data-arg="${a.action[2] || ''}">${a.action[0]}</button></div></li>`
      )
      .join('');

    return `<header class="topbar"><div><h1 tabindex="-1">Denah</h1>
        <p class="topbar__sub">September 2026 · ±7 Rabiul Akhir 1448</p></div><span class="badge">Mode demo</span></header>
      <section class="card card--hero" aria-label="Ringkasan rezeki bulan ini">
        <p class="hero-label">Rezeki bulan ini</p>
        <p class="hero-number">${rp(total)}</p>
        ${stackBar(items, pend, aria)}
        ${legend(items, total, pend)}
        ${
          pend
            ? `<div class="pending"><span>${icon('alert')}</span><span class="pending__text">${rp(pend)} belum dialirkan</span>
               <button type="button" class="btn btn--tonal btn--small" data-action="alirkan-pending">Alirkan</button></div>`
            : ''
        }
      </section>
      <h2 class="section-title">Ruang</h2>
      <div class="rooms-grid">${cards}
        <button type="button" class="room-card room-card--add" data-action="info" data-arg="Menambah ruang (S10) belum dibuat di prototipe. Gratis sampai 5 ruang.">${icon('plus')}Ruang baru</button>
      </div>
      <h2 class="section-title">Perlu perhatian</h2>
      <ul class="list">${attnHtml}</ul>`;
  }

  function catatScreen() {
    const d = draft;
    const tabs = `<div class="seg-control" role="tablist" aria-label="Jenis transaksi">
      <button type="button" role="tab" aria-selected="${d.mode === 'masuk'}" data-action="mode" data-arg="masuk">Pemasukan</button>
      <button type="button" role="tab" aria-selected="${d.mode === 'keluar'}" data-action="mode" data-arg="keluar">Pengeluaran</button>
      <button type="button" role="tab" aria-selected="false" disabled title="Belum dibuat di prototipe">Transfer</button></div>`;

    let fields = '';
    if (d.mode === 'masuk') {
      const chips = ['Gaji', 'Usaha', 'Freelance', 'Lainnya']
        .map((s) => `<button type="button" class="chip-btn" aria-pressed="${d.sumber === s}" data-action="sumber" data-arg="${s}">${s}</button>`)
        .join('');
      fields = `<p class="field-label">Sumber</p><div class="chips">${chips}</div>`;
    } else {
      const r = S.rooms[d.room];
      if (!d.cat || !catsOf(r).some((p) => p.name === d.cat)) d.cat = catsOf(r)[0].name;
      const roomChips = rooms()
        .map(
          (x) =>
            `<button type="button" class="chip-btn" aria-pressed="${d.room === x.id}" data-action="room-pick" data-arg="${x.id}"><span class="dot" style="--seg:${color(x)}"></span>${esc(x.name)}</button>`
        )
        .join('');
      const catChips = catsOf(r)
        .map((p) => `<button type="button" class="chip-btn" aria-pressed="${d.cat === p.name}" data-action="cat-pick" data-arg="${esc(p.name)}">${esc(p.name)}</button>`)
        .join('');
      fields = `<p class="field-label">Ruang</p><div class="chips">${roomChips}</div>
        <p class="field-label">Kategori</p><div class="chips">${catChips}</div>
        <p class="field-label">Akun</p><div class="chips">${acctChips(d.acct, 'acct-pick')}</div>
        <div class="switch-row"><div><b>Jadikan favorit</b><p class="list-row__sub">Muncul di Catat kilat dan tersimpan dengan satu ketukan.</p></div>
          <button type="button" class="switch" role="switch" aria-checked="${d.fav}" aria-label="Jadikan favorit" data-action="toggle-fav"></button></div>`;
    }

    const rows = `<ul class="list field-rows">
      <li><button type="button" class="list-row" data-action="info" data-arg="Pemilih akun belum dibuat di prototipe.">
        <span class="list-row__main"><p class="list-row__sub">Akun</p><p class="list-row__title">${esc(S.acct[d.acct].name)}</p></span>${icon('chevron')}</button></li>
      <li><button type="button" class="list-row" data-action="info" data-arg="Pemilih tanggal belum dibuat di prototipe.">
        <span class="list-row__main"><p class="list-row__sub">Tanggal</p><p class="list-row__title">Hari ini</p></span>${icon('chevron')}</button></li></ul>`;

    const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '000', '0', 'back']
      .map((k) =>
        k === 'back'
          ? `<button type="button" class="key" data-action="key" data-arg="back" aria-label="Hapus angka terakhir">${icon('backspace')}</button>`
          : `<button type="button" class="key" data-action="key" data-arg="${k}">${k}</button>`
      )
      .join('');

    const primary =
      d.mode === 'masuk'
        ? `<button type="button" class="btn btn--primary btn--block" id="btn-primary" data-action="preview">Lihat pembagian</button>`
        : `<button type="button" class="btn btn--primary btn--block" id="btn-primary" data-action="save-expense">Simpan</button>
           <button type="button" class="btn btn--text btn--block" id="btn-add" data-action="save-add">Simpan dan tambah lagi</button>`;

    return `<header class="topbar topbar--nav"><button type="button" class="icon-btn" data-action="back" aria-label="Tutup">${icon('close')}</button><h1 tabindex="-1">Catat</h1></header>
      ${tabs}
      <div class="amount" aria-live="polite"><span class="amount__cur">Rp</span><span class="amount__num" id="amount-num"></span></div>
      ${fields}${d.mode === 'masuk' ? rows : ''}
      <div class="banner" id="banner" role="status" hidden></div>
      <div class="numpad">${keys}</div>
      <div class="actions">${primary}</div>`;
  }

  function updateCatat() {
    const d = draft;
    const n = Number(d.amount || 0);
    const el = $('#amount-num');
    if (!el) return;
    el.textContent = n ? nf.format(n) : '0';
    el.classList.toggle('is-empty', !n);
    $('#btn-primary').disabled = !n;
    const add = $('#btn-add');
    if (add) add.disabled = !n;
    const banner = $('#banner');
    if (d.mode === 'keluar') {
      const r = S.rooms[d.room];
      const over = r.used + n - r.alloc;
      if (n && over > 0) {
        banner.hidden = false;
        banner.innerHTML = `${icon('alert')}<span>Jatah ${esc(r.name)} bulan ini terlampaui ${rp(over)}. Kamu tetap bisa menyimpannya.</span>`;
      } else {
        banner.hidden = true;
      }
    } else {
      banner.hidden = true;
    }
  }

  function alokasiScreen() {
    const { parts, total, rest, valid } = split(A.amount, A.pcts);
    const items = rooms().map((r) => ({ id: r.id, name: r.name, color: color(r), to: parts[r.id], from: parts[r.id] }));
    const aria = 'Pembagian: ' + items.map((i) => `${i.name} ${rp(i.to)}`).join(', ') + (rest ? `, belum dialirkan ${rp(rest)}` : '');
    const rows = rooms()
      .map((r) => {
        const p = A.pcts[r.id];
        const ctrl = A.edit
          ? `<span class="stepper"><button type="button" data-action="step" data-arg="${r.id}:-5" aria-label="Kurangi ${esc(r.name)} 5 persen">−</button><output>${p}%</output><button type="button" data-action="step" data-arg="${r.id}:5" aria-label="Tambah ${esc(r.name)} 5 persen">+</button></span>`
          : `<p class="alloc-row__pct">${p}%</p>`;
        return `<div class="alloc-row">${roomIcon(r)}<div class="alloc-row__main"><p class="alloc-row__name">${esc(r.name)}</p>${A.edit ? '' : ctrl}</div>${A.edit ? ctrl : ''}<span class="alloc-row__amt">${rp(parts[r.id])}</span></div>`;
      })
      .join('');
    return `${topbarNav('Alirkan rezeki')}
      <p class="hero-label">${esc(A.label)}</p>
      <p class="hero-number hero-number--sm">${rp(A.amount)}</p>
      ${stackBar(items, rest, aria)}
      ${legend(items, A.amount, rest)}
      <div class="card" style="margin-top:var(--rf-space-4)">
        ${rows}
        <div class="total-line"><span>Belum dialirkan</span><span>${rp(rest)}</span></div>
      </div>
      ${
        valid
          ? ''
          : `<div class="banner">${icon('alert')}<span>Total ${total}% lebih dari 100%. Kurangi salah satu ruang.</span></div>`
      }
      <div class="switch-row"><div><b>Ubah sekali ini</b><p class="list-row__sub">${A.edit ? 'Hanya untuk pemasukan ini. Aturan tetap tidak berubah.' : 'Sesuaikan pembagian tanpa mengubah aturan.'}</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${A.edit}" aria-label="Ubah sekali ini" data-action="toggle-edit"></button></div>
      <button type="button" class="btn btn--primary btn--block" data-action="confirm-alokasi" ${valid ? '' : 'disabled'}>Alirkan</button>`;
  }

  function detailScreen() {
    const r = S.rooms[route.id];
    const st = status(r);
    const to = Math.min(1, st.ratio);
    const zakat =
      r.id === 'memberi'
        ? `<button type="button" class="zakat-card" style="--seg:${color(r)}" data-action="open-haul">
            ${ring(117 / 354, 117 / 354, r, '33%')}
            <span class="list-row__main"><span class="list-row__title">Zakat mal</span>
            <p class="list-row__sub">Haul berjalan · hari ke-117 dari 354</p></span>${icon('chevron')}</button>`
        : '';
    const pos = posList(r)
      .map((p) => {
        const ratio = p.alloc ? p.used / p.alloc : 0;
        const over = p.used - p.alloc;
        if (!p.alloc) {
          return `<li><div class="pos" style="--seg:${color(r)}"><div class="pos__top"><span>${esc(p.name)}</span><span>${rp(p.used)}</span></div>
            <p class="pos__sub">Tanpa jatah tersendiri. Ikut dihitung dalam jatah ${esc(r.name)}.</p></div></li>`;
        }
        return `<li><div class="pos" style="--seg:${color(r)}"><div class="pos__top"><span>${esc(p.name)}</span><span>${pct(ratio)}%</span></div>
          <p class="pos__sub">${rp(p.used)} dari ${rp(p.alloc)}</p>
          <div class="bar" role="img" aria-label="${esc(p.name)} ${pct(ratio)}%"><div class="bar__fill" style="width:${Math.min(100, ratio * 100).toFixed(1)}%"></div></div>
          ${over > 0 ? `<p class="over">${icon('alert')}Melebihi jatah ${rp(over)}</p>` : ''}</div></li>`;
      })
      .join('');
    const tx = S.tx.filter((t) => t.room === r.id).slice(0, 4);
    const txHtml = tx.length
      ? tx
          .map(
            (t) => `<li><div class="list-row"><span class="list-row__main"><p class="list-row__title">${esc(t.title)}</p><p class="list-row__sub">${esc(t.date)} · ${esc(t.sub)}</p></span>
            <span class="list-row__end">${rp(t.amt)}</span></div></li>`
          )
          .join('')
      : `<li><p class="empty">Belum ada transaksi di ruang ini.</p></li>`;
    return `${topbarNav(esc(r.name))}
      <div class="detail-head" style="--seg:${color(r)}">${ring(to, to, r, undefined, 'ring--lg', st.ratio)}
        <div><p class="metric"><b>${rp(r.used)}</b>${r.verb} dari jatah ${rp(r.alloc)}</p>
        <p class="list-row__sub" style="margin:6px 0">Tipe ruang: ${r.kindLabel}</p>${chip(st)}</div></div>
      ${zakat}
      <h2 class="section-title">Pos</h2><ul class="list">${pos}</ul>
      <h2 class="section-title">Transaksi terbaru</h2><ul class="list">${txHtml}</ul>
      <div style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="info" data-arg="Pengaturan aturan (S12) belum dibuat di prototipe.">Atur aturan ${esc(r.name)}</button></div>`;
  }

  function haulScreen() {
    const r = S.rooms.memberi;
    return `${topbarNav('Zakat mal')}
      <section class="haul-hero" style="--seg:${color(r)}" aria-label="Progres haul">
        ${ring(117 / 354, 117 / 354, r, '<span>117<small>dari 354 hari</small></span>')}
        <span class="chip chip--progress">${icon('clock')}Haul berjalan</span>
      </section>
      <div class="card"><dl class="kv">
        <div><dt>Mulai</dt><dd>8 Dzulhijjah 1447<br /><span class="muted">±25 Mei 2026</span></dd></div>
        <div><dt>Jatuh tempo</dt><dd>8 Dzulhijjah 1448<br /><span class="muted">±14 Mei 2027</span></dd></div>
        <div><dt>Sisa</dt><dd>237 hari</dd></div></dl></div>
      <h2 class="section-title">Nisab</h2>
      <div class="card"><dl class="kv">
        <div><dt>Harta bersih</dt><dd>${rp(152000000)}</dd></div>
        <div><dt>Nisab hari ini</dt><dd>${rp(141100000)}<br /><span class="muted">85 g × ${rp(1660000)} per g</span></dd></div>
        <div><dt>Status</dt><dd><span class="chip chip--good">${icon('check')}Di atas nisab</span></dd></div>
        <div class="kv--total"><dt>Perkiraan zakat (2,5%)</dt><dd>${rp(3800000)}</dd></div></dl></div>
      <details class="card" style="margin-top:var(--rf-space-3)"><summary>Lihat rincian perhitungan</summary>
        <dl class="kv">
          <div><dt>Emas 60 g</dt><dd>${rp(99600000)}</dd></div>
          <div><dt>Tabungan dan deposito</dt><dd>${rp(38000000)}</dd></div>
          <div><dt>Reksa dana</dt><dd>${rp(25000000)}</dd></div>
          <div><dt>Piutang lancar</dt><dd>${rp(4000000)}</dd></div>
          <div><dt>Hutang jatuh tempo</dt><dd>${rp(-14600000)}</dd></div>
          <div class="kv--total"><dt>Harta bersih</dt><dd>${rp(152000000)}</dd></div></dl></details>
      <p class="disclaimer">Asumsi: nisab 85 gram emas, tarif 2,5%, haul 1 tahun Hijriyah. Tanggal Hijriyah adalah perkiraan (metode kalender belum diputuskan). Harga emas dan angka lain hanya contoh. Ini bantuan hitung, bukan fatwa.</p>
      <div class="actions" style="margin-top:var(--rf-space-4)">
        <button type="button" class="btn btn--tonal btn--block" data-action="info" data-arg="Ubah nilai harta (S15) belum dibuat di prototipe.">Perbarui nilai harta</button>
        <button type="button" class="btn btn--primary btn--block" disabled>Tunaikan zakat (aktif saat haul genap)</button></div>`;
  }

  function transaksiScreen() {
    const src = route.acct ? S.tx.filter((t) => t.acct === route.acct) : S.tx;
    const groups = [];
    src.forEach((t) => {
      let g = groups.find((x) => x.date === t.date);
      if (!g) groups.push((g = { date: t.date, items: [] }));
      g.items.push(t);
    });
    const html =
      groups
        .map((g) => {
          const rows = g.items
            .map((t) => {
              const r = t.room ? S.rooms[t.room] : null;
              const tile = r ? roomIcon(r) : `<span class="room-icon" style="--seg:var(--rf-primary)">${icon('income')}</span>`;
              return `<li><div class="list-row">${tile}<span class="list-row__main"><p class="list-row__title">${esc(t.title)}</p><p class="list-row__sub">${esc(t.sub)}</p></span>
              <span class="list-row__end">${t.amt > 0 ? '+' : ''}${rp(t.amt)}</span></div></li>`;
            })
            .join('');
          return `<h2 class="section-title">${esc(g.date)}</h2><ul class="list">${rows}</ul>`;
        })
        .join('') || `<p class="empty">Belum ada transaksi di akun ini.</p>`;

    const filter = route.acct
      ? `<div class="chips" style="margin:var(--rf-space-2) 0"><button type="button" class="chip-btn" aria-pressed="true" data-action="clear-filter">Akun: ${esc(S.acct[route.acct].name)} ✕</button></div>`
      : '';
    // Petunjuk hari kosong: lembut, tanpa streak dan tanpa warna merah.
    const yesterdayEmpty = !S.checked['19 Sep'] && !S.tx.some((t) => t.date === '19 Sep' && t.amt < 0);
    const hint =
      !route.acct && yesterdayEmpty
        ? `<div class="banner banner--info">${icon('clock')}<span class="banner__body">Kemarin belum ada catatan pengeluaran.
            <span class="banner__actions"><button type="button" class="btn btn--tonal btn--small" data-action="hint-catat">Catat sekarang</button>
            <button type="button" class="btn btn--text btn--small" data-action="hint-none">Tidak ada</button></span></span></div>`
        : '';
    const drafRow =
      !route.acct && S.capture.perm && S.drafts.length
        ? `<ul class="list" style="margin-top:var(--rf-space-3)"><li><button type="button" class="list-row" data-action="open-draf">
            <span class="attn-icon" style="color:var(--rf-primary)">${icon('inbox')}</span>
            <span class="list-row__main"><p class="list-row__title">${S.drafts.length} draf menunggu</p><p class="list-row__sub">Dari notifikasi bank dan e-wallet</p></span>${icon('chevron')}</button></li></ul>`
        : '';
    return `<header class="topbar"><div><h1 tabindex="-1">Transaksi</h1>
      <p class="topbar__sub">Versi ringkas (S08): pencarian belum dibuat</p></div></header>${filter}${hint}${drafRow}${html}`;
  }

  function ruangScreen() {
    const rows = rooms()
      .map((r) => {
        const st = status(r);
        return `<li><button type="button" class="list-row" data-action="open-room" data-arg="${r.id}">${roomIcon(r)}
          <span class="list-row__main"><p class="list-row__title">${esc(r.name)}</p><p class="list-row__sub">${r.kindLabel} · aturan ${r.pct}% · ${st.label}</p></span>${icon('chevron')}</button></li>`;
      })
      .join('');
    return `<header class="topbar"><div><h1 tabindex="-1">Ruang</h1>
      <p class="topbar__sub">Versi ringkas (S10)</p></div></header>
      <ul class="list">${rows}</ul>
      <div style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="info" data-arg="Menambah ruang belum dibuat di prototipe. Gratis sampai 5 ruang; lebih dari itu butuh Pro.">${icon('plus')}Tambah ruang</button>
      <p class="list-row__sub" style="text-align:center;margin-top:8px">3 dari 5 ruang gratis</p></div>`;
  }

  function lainnyaScreen() {
    const row = (title, sub, arg, end) =>
      `<li><button type="button" class="list-row" data-action="info" data-arg="${arg}"><span class="list-row__main"><p class="list-row__title">${title}</p><p class="list-row__sub">${sub}</p></span>${end || icon('chevron')}</button></li>`;
    const act = (title, sub, action, arg, iconName) =>
      `<li><button type="button" class="list-row" data-action="${action}" data-arg="${arg || ''}"><span class="attn-icon" style="color:var(--rf-primary)">${icon(iconName)}</span>
        <span class="list-row__main"><p class="list-row__title">${title}</p><p class="list-row__sub">${sub}</p></span>${icon('chevron')}</button></li>`;
    const stale = accts().slice().sort((a, b) => b.days - a.days)[0].id;
    return `<header class="topbar"><div><h1 tabindex="-1">Lainnya</h1><p class="topbar__sub">Versi ringkas (S18)</p></div></header>
      <ul class="list">
        <li><button type="button" class="list-row" data-action="pro"><span class="attn-icon" style="color:var(--rf-primary)">${icon('star')}</span>
          <span class="list-row__main"><p class="list-row__title">Rizqflow Pro${S.pro ? ' (aktif)' : ''}</p><p class="list-row__sub">Sekali bayar, tanpa langganan</p></span>${icon('chevron')}</button></li>
        ${act('Koreksi saldo', 'S25 · cocokkan saldo akun dengan catatan', 'open-koreksi', stale, 'balance')}
        ${act('Pengingat harian', `S26 · ${S.reminder.on ? 'aktif, pukul ' + S.reminder.hour : 'dimatikan'}`, 'open-pengingat', '', 'bell')}
        ${act('Tangkap otomatis', 'S28 · Pro, versi 1.1', 'open-tangkap', '', 'inbox')}
        ${act('Widget catat kilat', 'Pro · pintasan ikon, tile, dan balasan notifikasi tetap gratis', 'open-widget', '', 'bolt')}
        ${row('Akun, kategori, dan favorit', 'S13', 'S13 belum dibuat di prototipe.')}
        ${row('Aturan alokasi', 'S12', 'S12 belum dibuat di prototipe.')}
        ${row('Keamanan', 'PIN dan biometrik (S19), selalu gratis', 'S19 belum dibuat di prototipe.')}
        ${row('Backup, restore, dan ekspor', 'S20, selalu gratis', 'S20 belum dibuat di prototipe.')}
        ${row('Tentang dan disclaimer', 'S23', 'S23 belum dibuat di prototipe.')}
      </ul>`;
  }

  /* --------------------------------- S24-S28: kemudahan mencatat (F8, F9) */

  const numpadHtml = (action) =>
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '000', '0', 'back']
      .map((k) =>
        k === 'back'
          ? `<button type="button" class="key" data-action="${action}" data-arg="back" aria-label="Hapus angka terakhir">${icon('backspace')}</button>`
          : `<button type="button" class="key" data-action="${action}" data-arg="${k}">${k}</button>`
      )
      .join('');

  function applyKey(a, arg) {
    if (arg === 'back') return a.slice(0, -1);
    if (arg === '000') return a && a.length <= 8 ? a + '000' : a;
    if (!(a === '' && arg === '0') && a.length < 11) return a + arg;
    return a;
  }

  const acctChips = (selected, action) =>
    accts()
      .map(
        (a) =>
          `<button type="button" class="chip-btn" aria-pressed="${selected === a.id}" data-action="${action}" data-arg="${a.id}">${esc(a.name)}</button>`
      )
      .join('');

  /** S25 Koreksi saldo: bandingkan saldo sebenarnya dengan catatan, tanpa menghakimi. */
  function koreksiScreen() {
    const a = S.acct[K2.acct];
    return `${topbarNav('Koreksi saldo')}
      <p class="field-label">Akun</p>
      <div class="chips">${acctChips(K2.acct, 'x-acct')}</div>
      <p class="hero-label" style="margin-top:var(--rf-space-4)">Saldo sebenarnya di ${esc(a.name)}</p>
      <div class="amount" aria-live="polite"><span class="amount__cur">Rp</span><span class="amount__num" id="x-num"></span></div>
      <div class="card"><dl class="kv">
        <div><dt>Menurut catatan</dt><dd>${rp(a.bal)}</dd></div>
        <div class="kv--total"><dt>Selisih</dt><dd id="x-diff">–</dd></div></dl></div>
      <div id="x-result" role="status"></div>
      <div class="numpad">${numpadHtml('x-key')}</div>`;
  }

  function updateKoreksi() {
    const num = $('#x-num');
    if (!num) return;
    const a = S.acct[K2.acct];
    const empty = K2.actual === '';
    const n = Number(K2.actual || 0);
    num.textContent = empty ? '0' : nf.format(n);
    num.classList.toggle('is-empty', empty);
    const diffEl = $('#x-diff');
    const out = $('#x-result');
    if (empty) {
      diffEl.textContent = '–';
      out.innerHTML = `<p class="list-row__sub" style="margin:var(--rf-space-3) 0">Ketik saldo yang kamu lihat di dompet atau aplikasi. Mencatat selisihnya tidak wajib.</p>`;
      return;
    }
    const diff = a.bal - n;
    diffEl.textContent = rp(Math.abs(diff));
    if (diff === 0) {
      out.innerHTML = `<div style="margin-top:var(--rf-space-3)"><span class="chip chip--good">${icon('check')}Catatan cocok dengan saldo</span></div>
        <button type="button" class="btn btn--primary btn--block" style="margin-top:var(--rf-space-3)" data-action="x-done">Selesai</button>`;
    } else if (diff > 0) {
      const roomChips = rooms()
        .map(
          (x) =>
            `<button type="button" class="chip-btn" aria-pressed="${K2.room === x.id}" data-action="x-room" data-arg="${x.id}"><span class="dot" style="--seg:${color(x)}"></span>${esc(x.name)}</button>`
        )
        .join('');
      const why = K2.room === lastRoomFor(K2.acct) ? 'Ruang dari pengeluaran terakhir di akun ini.' : 'Ruang pilihanmu.';
      out.innerHTML = `<div class="banner banner--info">${icon('clock')}<span>${rp(diff)} belum tercatat sebagai pengeluaran di ${esc(a.name)}. Tidak apa-apa, cukup dicatat sekali.</span></div>
        <p class="field-label">Dicatat sebagai Tak terlacak di ruang</p><div class="chips">${roomChips}</div>
        <p class="list-row__sub" style="margin:var(--rf-space-2) 0 var(--rf-space-3)">${why}</p>
        <div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="x-record">Catat selisih</button>
        <button type="button" class="btn btn--text btn--block" data-action="x-search">Cari sendiri dulu</button></div>`;
    } else {
      out.innerHTML = `<div class="banner banner--info">${icon('clock')}<span>Saldo sebenarnya ${rp(-diff)} lebih banyak dari catatan. Mungkin ada pemasukan yang belum tercatat.</span></div>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--primary btn--block" data-action="x-income">Catat pemasukan</button>
        <button type="button" class="btn btn--text btn--block" data-action="x-search">Cari sendiri dulu</button></div>`;
    }
  }

  /** Isi notifikasi malam. preview=true untuk pratinjau statis di S26. */
  function notifHtml(preview) {
    const nd = S.capture.perm ? S.drafts.length : 0;
    const body = `Ketik di sini, misalnya "kopi 25000".${nd ? ` ${nd} draf dari notifikasi menunggu.` : ''}`;
    const head = `<p class="notif__app">Rizqflow · ${preview ? 'pukul ' + S.reminder.hour : 'sekarang'}</p>
      <p class="notif__title">Ada pengeluaran yang belum dicatat hari ini?</p><p class="notif__body">${esc(body)}</p>`;
    if (preview) return `<div class="notif notif--static">${head}</div>`;
    return `${head}<div class="notif__reply"><input id="notif-input" type="text" autocomplete="off" placeholder="kopi 25000" aria-label="Balasan cepat, misalnya kopi 25000" />
      <button type="button" class="btn btn--tonal btn--small" data-action="notif-send">Kirim</button></div>
      <div class="notif__actions"><button type="button" class="btn btn--text btn--small" data-action="notif-kilat">Catat kilat</button>
      <button type="button" class="btn btn--text btn--small" data-action="notif-none">Tidak ada</button>
      <button type="button" class="btn btn--text btn--small" data-action="notif-close">Tutup</button></div>`;
  }

  /** S26 Pengingat harian. */
  function pengingatScreen() {
    const R = S.reminder;
    const hours = ['20.00', '21.00', '22.00']
      .map((h) => `<button type="button" class="chip-btn" aria-pressed="${R.hour === h}" data-action="reminder-hour" data-arg="${h}">${h}</button>`)
      .join('');
    return `${topbarNav('Pengingat harian')}
      <div class="switch-row"><div><b>Pengingat malam</b><p class="list-row__sub">Satu notifikasi per hari. Bisa dimatikan kapan saja.</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${R.on}" aria-label="Pengingat malam" data-action="toggle-reminder"></button></div>
      <p class="field-label">Jam pengingat</p><div class="chips">${hours}</div>
      <h2 class="section-title">Pratinjau notifikasi</h2>
      ${notifHtml(true)}
      <div style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="sim-notif">Simulasikan notifikasi malam</button></div>
      <p class="disclaimer">Usulan: izin notifikasi Android 13+ diminta setelah transaksi pertama disimpan, bukan di awal onboarding. Di prototipe tidak ada izin atau notifikasi sungguhan.</p>`;
  }

  /** S27 Draf dari notifikasi (v1.1, Pro). Draf tidak pernah tersimpan tanpa persetujuan. */
  function drafScreen() {
    const okAll = S.drafts.filter((d) => !d.dup).length;
    const cards = S.drafts
      .map((d) => {
        const acct = S.acct[d.acct];
        const r = S.rooms[d.room];
        return `<div class="card draft">
          <div class="draft__top"><b>${rp(d.amt)}</b><span class="muted">${esc(d.app)} · ${esc(d.time)}</span></div>
          <p class="list-row__title" style="margin:2px 0">${esc(d.to)}</p>
          <p class="list-row__sub">Akun ${esc(acct.name)} · usulan ${esc(r.name)} › ${esc(d.cat)}</p>
          ${
            d.dup
              ? `<p style="margin:var(--rf-space-2) 0 0"><span class="chip chip--attn">${icon('alert')}Mungkin sudah dicatat</span></p><p class="list-row__sub">${esc(d.dupNote)}</p>`
              : ''
          }
          <div class="draft__actions">
            <button type="button" class="btn btn--primary btn--small" data-action="draf-ok" data-arg="${d.id}">Setujui</button>
            <button type="button" class="btn btn--tonal btn--small" data-action="draf-edit" data-arg="${d.id}">Ubah</button>
            <button type="button" class="btn btn--text btn--small" data-action="draf-skip" data-arg="${d.id}">Abaikan</button></div></div>`;
      })
      .join('');
    return `${topbarNav('Draf dari notifikasi')}
      <p class="list-row__sub" style="margin:0 0 var(--rf-space-3)">Fitur v1.1 (Pro). Draf tidak pernah tersimpan tanpa persetujuanmu.</p>
      ${S.drafts.length ? cards : `<p class="empty">Belum ada draf. Pembayaran digital berikutnya akan muncul di sini.</p>`}
      ${okAll > 1 ? `<button type="button" class="btn btn--primary btn--block" data-action="draf-all">Setujui semua (${okAll})</button>` : ''}`;
  }

  /** S28 Tangkap otomatis (v1.1, Pro). Izin dan pengaturan sistem hanya disimulasikan. */
  function tangkapScreen() {
    const C = S.capture;
    const appRow = (id, label) =>
      `<div class="switch-row" style="margin:var(--rf-space-3) 0"><div><b>${esc(label)}</b><p class="list-row__sub">Dipetakan ke akun ${esc(S.acct[id].name)}</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${C.apps[id]}" aria-label="${esc(label)}" data-action="capture-app" data-arg="${id}"></button></div>`;
    return `${topbarNav('Tangkap otomatis')}
      <p class="list-row__sub" style="margin:0 0 var(--rf-space-3)">Fitur v1.1 · Pro. Rizqflow menyiapkan draf dari notifikasi pembayaran; kamu yang menyetujui.</p>
      <div class="card"><dl class="kv">
        <div><dt>Yang dibaca</dt><dd>Hanya notifikasi dari aplikasi di daftar bawah</dd></div>
        <div><dt>Yang disimpan</dt><dd>Nominal, tujuan, waktu, aplikasi. Teks asli dibuang</dd></div>
        <div><dt>Diproses</dt><dd>Di perangkat, tidak dikirim ke mana pun</dd></div></dl></div>
      <h2 class="section-title">Akses notifikasi</h2>
      <div class="card">
        <p style="margin:0 0 var(--rf-space-3)">${C.perm ? `<span class="chip chip--good">${icon('check')}Akses aktif</span>` : `<span class="chip chip--progress">${icon('clock')}Belum diberi akses</span>`}</p>
        <button type="button" class="btn btn--tonal btn--block" data-action="capture-perm">${C.perm ? 'Cabut akses (simulasi)' : 'Buka pengaturan akses notifikasi'}</button>
        <p class="list-row__sub" style="margin-top:var(--rf-space-2)">Android membuka pengaturan sistem untuk izin ini; di prototipe hanya disimulasikan.</p></div>
      <h2 class="section-title">Aplikasi yang didukung</h2>
      <div class="card">${appRow('gopay', 'GoPay')}${appRow('jago', 'Bank Jago')}</div>
      <div class="actions" style="margin-top:var(--rf-space-4)">
        <button type="button" class="btn btn--tonal btn--block" data-action="capture-sim" ${C.perm ? '' : 'disabled'}>Simulasikan pembayaran masuk</button>
        ${S.drafts.length ? `<button type="button" class="btn btn--primary btn--block" data-action="open-draf">Lihat draf (${S.drafts.length})</button>` : ''}
      </div>
      <p class="disclaimer">Daftar aplikasi diperluas bertahap. Notifikasi dari aplikasi lain diabaikan dan tidak dibaca lebih jauh.</p>`;
  }

  const SCREENS = {
    denah: denahScreen, catat: catatScreen, alokasi: alokasiScreen, detail: detailScreen,
    haul: haulScreen, transaksi: transaksiScreen, ruang: ruangScreen, lainnya: lainnyaScreen,
    koreksi: koreksiScreen, pengingat: pengingatScreen, draf: drafScreen, tangkap: tangkapScreen,
  };
  const NO_NAV = ['catat', 'alokasi', 'haul', 'koreksi'];
  const TABS = ['denah', 'transaksi', 'ruang', 'lainnya'];

  /* --------------------------------------------------------------- router */

  const phone = $('#phone');
  const screenEl = $('#screen');
  const nav = $('#nav');
  const fab = document.createElement('button');
  fab.type = 'button';
  fab.className = 'fab';
  fab.dataset.action = 'catat';
  fab.innerHTML = `${icon('plus')}Catat`;
  fab.hidden = true;
  phone.appendChild(fab);

  // Notifikasi malam (F8) ditampilkan sebagai kartu di atas ponsel.
  const notifEl = document.createElement('div');
  notifEl.className = 'notif';
  notifEl.id = 'notif';
  notifEl.setAttribute('role', 'alert');
  notifEl.hidden = true;
  phone.appendChild(notifEl);

  // Snackbar lama ditutup saat pindah layar supaya tidak menutupi kontrol layar berikutnya.
  const dismissToast = () => $('#toast').classList.remove('is-open');

  function go(name, params, opts) {
    dismissToast();
    if (opts && opts.reset) stack = [];
    else stack.push(route);
    route = Object.assign({ name }, params);
    render();
  }

  function goTab(name) {
    dismissToast();
    stack = [];
    route = { name };
    render();
  }

  function back() {
    dismissToast();
    route = stack.pop() || { name: 'denah' };
    render();
  }

  function activeTab() {
    if (TABS.includes(route.name)) return route.name;
    if (route.name === 'detail') return route.tab || 'ruang';
    if (route.name === 'pengingat' || route.name === 'tangkap') return 'lainnya';
    if (route.name === 'draf') return 'transaksi';
    return 'denah';
  }

  function render() {
    const html = SCREENS[route.name]();
    screenEl.innerHTML = html;
    screenEl.scrollTop = 0;
    const noNav = NO_NAV.includes(route.name);
    screenEl.classList.toggle('no-nav', noNav);
    nav.hidden = noNav;
    fab.hidden = !(route.name === 'denah' || route.name === 'transaksi');
    phone.dataset.screen = route.name;
    $$('.nav__item', nav).forEach((b) => {
      if (b.dataset.arg === activeTab()) b.setAttribute('aria-current', 'page');
      else b.removeAttribute('aria-current');
    });
    if (route.name === 'catat') updateCatat();
    if (route.name === 'koreksi') updateKoreksi();
    const h1 = $('h1', screenEl);
    if (h1) h1.focus({ preventScroll: true });
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        $$('[data-to-dash]', screenEl).forEach((c) => c.setAttribute('stroke-dasharray', `${c.dataset.toDash} 100`));
        $$('[data-to-grow]', screenEl).forEach((s) => (s.style.flexGrow = s.dataset.toGrow));
      })
    );
  }

  /* ----------------------------------------------------------- aksi/event */

  function toast(msg, action) {
    const t = $('#toast');
    clearTimeout(toastTimer);
    t.innerHTML = `<span>${esc(msg)}</span>${action ? `<button type="button" data-action="undo">${esc(action)}</button>` : ''}`;
    t.classList.add('is-open');
    toastTimer = setTimeout(() => t.classList.remove('is-open'), action ? 5000 : 3200);
  }

  const PRO_BENEFITS = {
    pro: [
      'Ruang peran tak terbatas, lengkap dengan sistem per peran',
      'Aturan alokasi lanjutan: prioritas, batas atas, sisa mengalir',
      'Multi-profil zakat dengan pengingat haul',
    ],
    capture: [
      'Catat pembayaran digital otomatis dari notifikasi, sebagai draf yang kamu setujui',
      'Widget catat kilat di layar utama',
      'Aturan alokasi lanjutan: prioritas, batas atas, sisa mengalir',
    ],
    widget: [
      'Widget catat kilat di layar utama',
      'Catat pembayaran digital otomatis dari notifikasi (v1.1)',
      'Ruang peran tak terbatas, lengkap dengan sistem per peran',
    ],
  };

  function showSheet(html, label) {
    const sheet = $('#sheet');
    sheet.setAttribute('aria-label', label);
    sheet.innerHTML = html;
    $('#scrim').hidden = false;
    sheet.hidden = false;
  }

  function openSheet(reason, after) {
    sheetAfter = after || null;
    const items = (PRO_BENEFITS[reason] || PRO_BENEFITS.pro)
      .map((t) => `<li>${icon('check')}<span>${esc(t)}</span></li>`)
      .join('');
    const foot =
      reason === 'widget'
        ? 'Pintasan ikon, tile Quick Settings, dan balasan notifikasi untuk catat kilat tetap gratis.'
        : 'Fitur dasar dan zakat tetap gratis.';
    showSheet(
      `<div class="sheet__grab"></div>
      <h2>Rizqflow Pro</h2>
      <ul class="benefits">${items}</ul>
      <p class="price">${rp(129000)} <span class="muted" style="font-weight:500">sekali bayar</span></p>
      <p class="list-row__sub" style="margin:2px 0 var(--rf-space-4)">Tanpa langganan. Harga hanya contoh.</p>
      <div class="actions">
        <button type="button" class="btn btn--primary btn--block" data-action="buy">Beli</button>
        <button type="button" class="btn btn--text btn--block" data-action="info" data-arg="Pulihkan pembelian: simulasi, tidak ada akun toko.">Pulihkan pembelian</button>
        <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Nanti saja</button>
      </div>
      <p class="disclaimer" style="text-align:center">${foot}</p>`,
      'Rizqflow Pro'
    );
    const first = $('.btn--primary', $('#sheet'));
    if (first) first.focus();
  }

  function closeSheet() {
    sheetAfter = null;
    $('#scrim').hidden = true;
    $('#sheet').hidden = true;
  }

  function startAlokasi(label, amount, pendingId, acct) {
    A = {
      label, amount, pendingId,
      acct: acct || 'jago',
      pcts: Object.fromEntries(ORDER.map((id) => [id, S.rooms[id].pct])),
      edit: false,
    };
    go('alokasi');
  }

  function confirmAlokasi() {
    const { parts, valid } = split(A.amount, A.pcts);
    if (!valid) return;
    prevView = snapshot();
    const ids = ORDER.filter((id) => parts[id] > 0);
    ids.forEach((id) => (S.rooms[id].alloc += parts[id]));
    if (A.pendingId) S.pending = S.pending.filter((p) => p.id !== A.pendingId);
    S.acct[A.acct].bal += A.amount;
    S.tx.unshift({ date: 'Hari ini', title: A.label, sub: `Pemasukan · dialirkan ke ${ids.length} ruang`, amt: A.amount, room: null, acct: A.acct });
    const msg = `${rp(A.amount)} dialirkan ke ${ids.length} ruang`;
    A = null;
    stack = [];
    route = { name: 'denah', flow: ids };
    render();
    toast(msg);
  }

  function saveExpense(keepOpen) {
    const n = Number(draft.amount || 0);
    if (!n) return;
    const r = S.rooms[draft.room];
    const before = JSON.stringify(S);
    addExpense({ n, room: draft.room, cat: draft.cat, acct: draft.acct });
    S.last = { room: draft.room, cat: draft.cat };
    S.lastAcct = draft.acct;
    let extra = '';
    if (draft.fav && !S.favs.some((f) => f.name === draft.cat && f.amt === n)) {
      S.favs.push({ id: 'f' + (S.favs.length + 1) + '-' + n, name: draft.cat, amt: n, room: draft.room, cat: draft.cat, acct: draft.acct });
      extra = ' dan dijadikan favorit';
    }
    if (draft.fromDraft) S.drafts = S.drafts.filter((x) => x.id !== draft.fromDraft);
    undo = () => {
      S = JSON.parse(before);
      render();
    };
    if (keepOpen) {
      draft.amount = '';
      draft.fav = false;
      draft.fromDraft = null;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    } else {
      draft = newDraft();
      stack = [];
      route = { name: 'denah' };
      render();
    }
    toast(`${rp(n)} tersimpan di ${r.name}${extra}`, 'Urungkan');
  }

  /* ------------------------------- logika catat kilat, notifikasi, draf */

  const SAMPLES = [
    { app: 'GoPay', to: 'Kopi Tuku', amt: 25000, acct: 'gopay', time: '08.12', room: 'keluarga', cat: 'Lain-lain' },
    { app: 'Bank Jago', to: 'Indomaret', amt: 47500, acct: 'jago', time: '12.40', room: 'keluarga', cat: 'Belanja bulanan' },
    { app: 'GoPay', to: 'Parkir Mall', amt: 5000, acct: 'gopay', time: '13.05', room: 'keluarga', cat: 'Lain-lain', dup: true, dupNote: 'Ada catatan Rp 5.000 di GoPay dalam 10 menit terakhir.' },
    { app: 'GoPay', to: 'Warung Bu Sri', amt: 32000, acct: 'gopay', time: '19.20', room: 'keluarga', cat: 'Belanja bulanan' },
    { app: 'Bank Jago', to: 'Token PLN', amt: 100000, acct: 'jago', time: '20.02', room: 'keluarga', cat: 'Listrik dan air' },
  ];

  function pushSample() {
    const smp = SAMPLES[S.sampleIdx % SAMPLES.length];
    S.sampleIdx += 1;
    S.drafts.push(Object.assign({ id: 'd' + S.sampleIdx }, smp));
    return smp;
  }

  function grantCapture() {
    S.capture.perm = true;
    if (!S.drafts.length && S.sampleIdx === 0) {
      pushSample();
      pushSample();
      pushSample();
    }
  }

  /** Simpan pengeluaran dari jalur cepat (favorit, nominal, balasan notifikasi), dengan Urungkan. */
  function quickSave(e) {
    const before = JSON.stringify(S);
    addExpense(e);
    S.last = { room: e.room, cat: e.cat };
    S.lastAcct = e.acct;
    undo = () => {
      S = JSON.parse(before);
      render();
    };
    closeSheet();
    hideNotif();
    render();
    toast(`${e.title} ${rp(e.n)} tersimpan di ${S.rooms[e.room].name}`, 'Urungkan');
  }

  /** Balasan cepat di notifikasi: "kopi 25000", "parkir 3rb", "makan siang 35k". */
  function quickReply(text) {
    const m = text.trim().match(/^(.*?)[\s:]*([0-9][0-9.]*)\s*(rb|ribu|k|jt)?$/i);
    if (!m) return null;
    let n = Number(m[2].replace(/\./g, ''));
    const u = (m[3] || '').toLowerCase();
    if (u === 'rb' || u === 'ribu' || u === 'k') n *= 1000;
    else if (u === 'jt') n *= 1000000;
    if (!n) return null;
    const note = m[1].trim();
    const fav = note && S.favs.find((f) => f.name.toLowerCase() === note.toLowerCase());
    const title = note ? note.charAt(0).toUpperCase() + note.slice(1) : 'Pengeluaran';
    return {
      n, title,
      room: fav ? fav.room : S.last.room,
      cat: fav ? fav.cat : S.last.cat,
      acct: fav ? fav.acct : S.lastAcct,
    };
  }

  function openKilat() {
    K = { amount: '', acct: S.lastAcct || 'tunai' };
    renderKilat();
  }

  function renderKilat() {
    const favs = S.favs
      .slice(0, 6)
      .map((f) => `<button type="button" class="chip-btn" data-action="fav-save" data-arg="${f.id}">${esc(f.name)} ${rpShort(f.amt)}</button>`)
      .join('');
    showSheet(
      `<div class="sheet__grab"></div>
      <div class="sheet__head"><h2>Catat kilat</h2><button type="button" class="icon-btn" data-action="close-sheet" aria-label="Tutup">${icon('close')}</button></div>
      <div class="amount" aria-live="polite"><span class="amount__cur">Rp</span><span class="amount__num" id="kilat-num"></span></div>
      <p class="field-label">Favorit (ketuk untuk langsung menyimpan)</p>
      <div class="chips">${favs || '<span class="list-row__sub">Belum ada favorit. Jadikan favorit dari layar Catat.</span>'}</div>
      <p class="field-label">Akun</p><div class="chips">${acctChips(K.acct, 'kilat-acct')}</div>
      <div class="numpad">${numpadHtml('kilat-key')}</div>
      <button type="button" class="btn btn--primary btn--block" id="kilat-save" data-action="kilat-save">Simpan</button>`,
      'Catat kilat'
    );
    updateKilat();
  }

  function updateKilat() {
    const el = $('#kilat-num');
    if (!el) return;
    const n = Number(K.amount || 0);
    el.textContent = n ? nf.format(n) : '0';
    el.classList.toggle('is-empty', !n);
    $('#kilat-save').disabled = !n;
  }

  function showNotif() {
    closeSheet();
    notifEl.innerHTML = notifHtml(false);
    notifEl.hidden = false;
    const input = $('#notif-input');
    if (input) input.focus({ preventScroll: true });
  }

  function hideNotif() {
    notifEl.hidden = true;
  }

  /** Setujui satu draf menjadi transaksi. */
  function approveDraft(d) {
    addExpense({ n: d.amt, room: d.room, cat: d.cat, acct: d.acct, title: d.to });
    S.drafts = S.drafts.filter((x) => x.id !== d.id);
  }

  const ACTIONS = {
    tab: (arg) => goTab(arg),
    catat: () => {
      draft = newDraft();
      go('catat');
    },
    back: () => back(),
    mode: (arg) => {
      draft.mode = arg;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    sumber: (arg) => {
      draft.sumber = arg;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    'room-pick': (arg) => {
      draft.room = arg;
      draft.cat = null;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    'cat-pick': (arg) => {
      draft.cat = arg;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    key: (arg) => {
      draft.amount = applyKey(draft.amount, arg);
      updateCatat();
    },
    preview: () => {
      const n = Number(draft.amount || 0);
      if (n) startAlokasi(draft.sumber, n, null, draft.acct);
    },
    'alirkan-pending': () => {
      const p = S.pending[0];
      if (p) startAlokasi(p.label, p.amount, p.id);
    },
    'toggle-edit': () => {
      A.edit = !A.edit;
      screenEl.innerHTML = alokasiScreen();
    },
    step: (arg) => {
      const [id, d] = arg.split(':');
      A.pcts[id] = Math.max(0, Math.min(100, A.pcts[id] + Number(d)));
      screenEl.innerHTML = alokasiScreen();
    },
    'confirm-alokasi': () => confirmAlokasi(),
    'save-expense': () => saveExpense(false),
    'save-add': () => saveExpense(true),
    'open-room': (arg) => go('detail', { id: arg, tab: activeTab() }),
    'open-haul': () => go('haul'),
    pro: () => openSheet('pro'),
    'close-sheet': () => closeSheet(),
    buy: () => {
      const after = sheetAfter;
      S.pro = true;
      closeSheet();
      if (after && ACTIONS[after]) ACTIONS[after]();
      toast('Simulasi: tidak ada pembayaran sungguhan. Pro aktif di prototipe.');
    },
    /* --- S24 Catat kilat */
    kilat: () => openKilat(),
    'kilat-key': (arg) => {
      K.amount = applyKey(K.amount, arg);
      updateKilat();
    },
    'kilat-acct': (arg) => {
      K.acct = arg;
      renderKilat();
    },
    'kilat-save': () => {
      const n = Number(K.amount || 0);
      if (!n) return;
      quickSave({ n, room: S.last.room, cat: S.last.cat, acct: K.acct, title: S.last.cat });
    },
    'fav-save': (arg) => {
      const f = S.favs.find((x) => x.id === arg);
      if (f) quickSave({ n: f.amt, room: f.room, cat: f.cat, acct: f.acct, title: f.name });
    },
    'toggle-fav': () => {
      draft.fav = !draft.fav;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    'acct-pick': (arg) => {
      draft.acct = arg;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },

    /* --- notifikasi malam dan S26 */
    'sim-notif': () => {
      if (!S.reminder.on) {
        toast('Pengingat malam sedang dimatikan di Lainnya, Pengingat harian.');
        return;
      }
      showNotif();
    },
    'notif-send': () => {
      const input = $('#notif-input');
      const parsed = input ? quickReply(input.value) : null;
      if (!parsed) {
        toast('Belum terbaca. Coba ketik seperti: kopi 25000');
        return;
      }
      quickSave(parsed);
    },
    'notif-none': () => {
      S.checked['20 Sep'] = true;
      hideNotif();
      toast('Hari ini ditandai sudah dicek.');
    },
    'notif-kilat': () => {
      hideNotif();
      openKilat();
    },
    'notif-close': () => hideNotif(),
    'open-pengingat': () => go('pengingat'),
    'toggle-reminder': () => {
      S.reminder.on = !S.reminder.on;
      screenEl.innerHTML = pengingatScreen();
    },
    'reminder-hour': (arg) => {
      S.reminder.hour = arg;
      screenEl.innerHTML = pengingatScreen();
    },

    /* --- petunjuk hari kosong di S08 */
    'hint-catat': () => openKilat(),
    'hint-none': () => {
      S.checked['19 Sep'] = true;
      render();
      toast('Kemarin ditandai sudah dicek.');
    },
    'clear-filter': () => {
      route = { name: 'transaksi' };
      render();
    },

    /* --- S25 Koreksi saldo */
    'open-koreksi': (arg) => {
      const id = ACCT_ORDER.includes(arg) ? arg : 'tunai';
      K2 = { acct: id, actual: '', room: lastRoomFor(id) };
      go('koreksi');
    },
    'x-acct': (arg) => {
      K2 = { acct: arg, actual: '', room: lastRoomFor(arg) };
      screenEl.innerHTML = koreksiScreen();
      updateKoreksi();
    },
    'x-key': (arg) => {
      K2.actual = applyKey(K2.actual, arg);
      updateKoreksi();
    },
    'x-room': (arg) => {
      K2.room = arg;
      updateKoreksi();
    },
    'x-record': () => {
      const a = S.acct[K2.acct];
      const diff = a.bal - Number(K2.actual || 0);
      if (K2.actual === '' || diff <= 0) return;
      const before = JSON.stringify(S);
      const roomName = S.rooms[K2.room].name;
      addExpense({ n: diff, room: K2.room, cat: 'Tak terlacak', acct: K2.acct, title: 'Tak terlacak' });
      S.acct[K2.acct].days = 0;
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      back();
      toast(`${rp(diff)} dicatat sebagai Tak terlacak di ${roomName}`, 'Urungkan');
    },
    'x-done': () => {
      S.acct[K2.acct].days = 0;
      back();
      toast('Catatan cocok dengan saldo. Terima kasih sudah mencocokkan.');
    },
    'x-income': () => {
      const diff = Number(K2.actual || 0) - S.acct[K2.acct].bal;
      if (diff <= 0) return;
      S.acct[K2.acct].days = 0;
      draft = newDraft();
      draft.amount = String(diff);
      draft.sumber = 'Lainnya';
      draft.acct = K2.acct;
      go('catat');
    },
    'x-search': () => {
      dismissToast();
      stack = [];
      route = { name: 'transaksi', acct: K2.acct };
      render();
    },

    /* --- S27 dan S28 tangkap otomatis (v1.1, Pro) */
    'open-tangkap': () => {
      if (!S.pro) {
        openSheet('capture', 'open-tangkap');
        return;
      }
      go('tangkap');
    },
    'open-widget': () => {
      if (!S.pro) {
        openSheet('widget', 'open-widget');
        return;
      }
      toast('Simulasi: widget belum dibuat di prototipe.');
    },
    'capture-perm': () => {
      if (S.capture.perm) {
        S.capture.perm = false;
        screenEl.innerHTML = tangkapScreen();
        toast('Akses dicabut. Draf dan transaksi yang ada tetap.');
      } else {
        grantCapture();
        screenEl.innerHTML = tangkapScreen();
        toast(`Simulasi: akses diberikan. ${S.drafts.length} draf menunggu di tab Transaksi.`);
      }
    },
    'capture-app': (arg) => {
      S.capture.apps[arg] = !S.capture.apps[arg];
      screenEl.innerHTML = tangkapScreen();
    },
    'capture-sim': () => {
      const smp = SAMPLES[S.sampleIdx % SAMPLES.length];
      if (!S.capture.apps[smp.acct]) {
        S.sampleIdx += 1;
        toast(`Notifikasi ${smp.app} diabaikan karena aplikasinya dimatikan.`);
        return;
      }
      pushSample();
      screenEl.innerHTML = tangkapScreen();
      toast(`Draf baru: ${smp.to} ${rp(smp.amt)}`);
    },
    'open-draf': () => go('draf'),
    'draf-ok': (arg) => {
      const d = S.drafts.find((x) => x.id === arg);
      if (!d) return;
      const before = JSON.stringify(S);
      approveDraft(d);
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      render();
      toast(`${d.to} ${rp(d.amt)} tersimpan`, 'Urungkan');
    },
    'draf-all': () => {
      const list = S.drafts.filter((d) => !d.dup);
      if (!list.length) return;
      const before = JSON.stringify(S);
      list.forEach(approveDraft);
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      render();
      toast(`${list.length} draf disetujui`, 'Urungkan');
    },
    'draf-skip': (arg) => {
      S.drafts = S.drafts.filter((x) => x.id !== arg);
      render();
      toast('Draf diabaikan.');
    },
    'draf-edit': (arg) => {
      const d = S.drafts.find((x) => x.id === arg);
      if (!d) return;
      draft = newDraft();
      draft.mode = 'keluar';
      draft.amount = String(d.amt);
      draft.room = d.room;
      draft.cat = d.cat;
      draft.acct = d.acct;
      draft.fromDraft = d.id;
      go('catat');
    },
    info: (arg) => toast(arg),
    undo: () => {
      if (undo) undo();
      undo = null;
      $('#toast').classList.remove('is-open');
    },
  };

  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-action]');
    if (!el || el.disabled) return;
    const fn = ACTIONS[el.dataset.action];
    if (fn) fn(el.dataset.arg || '');
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!notifEl.hidden) hideNotif();
      else if (!$('#sheet').hidden) closeSheet();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && e.target && e.target.id === 'notif-input') ACTIONS['notif-send']();
  });

  /* ------------------------------------------------- panel: tema, ukuran */

  function store(k, v) {
    try {
      localStorage.setItem(k, v);
    } catch (_) {
      /* mode privat atau diblokir: abaikan */
    }
  }
  function load(k) {
    try {
      return localStorage.getItem(k);
    } catch (_) {
      return null;
    }
  }

  function setTheme(v) {
    if (v === 'light' || v === 'dark') document.documentElement.dataset.theme = v;
    else document.documentElement.removeAttribute('data-theme');
    $$('[data-control="theme"]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.value === v)));
    store('rf-theme', v);
  }

  function setScale(v) {
    document.documentElement.style.setProperty('--rf-scale', v);
    phone.dataset.scale = v;
    $$('[data-control="scale"]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.value === String(v))));
    store('rf-scale', v);
  }

  $$('[data-control]').forEach((b) =>
    b.addEventListener('click', () => {
      const c = b.dataset.control;
      if (c === 'theme') setTheme(b.dataset.value);
      else if (c === 'scale') setScale(b.dataset.value);
      else if (c === 'reset') {
        S = seed();
        prevView = null;
        draft = newDraft();
        A = null;
        K = null;
        K2 = null;
        hideNotif();
        closeSheet();
        stack = [];
        route = { name: 'denah' };
        render();
        toast('Data contoh dikembalikan.');
      }
    })
  );

  /* ---------------------------------------------------------------- mulai */

  const q = new URLSearchParams(location.search);
  setTheme(q.get('theme') || load('rf-theme') || 'auto');
  setScale(q.get('scale') || load('rf-scale') || '1');

  if (q.get('pro') === '1') S.pro = true;
  const start = q.get('screen') || 'denah';
  if (start.startsWith('detail-') && S.rooms[start.slice(7)]) {
    route = { name: 'detail', id: start.slice(7), tab: 'denah' };
    stack = [{ name: 'denah' }];
  } else if (start === 'alokasi') {
    const p = S.pending[0];
    A = { label: p.label, amount: p.amount, pendingId: p.id, acct: 'jago', edit: q.get('edit') === '1', pcts: Object.fromEntries(ORDER.map((id) => [id, S.rooms[id].pct])) };
    route = { name: 'alokasi' };
    stack = [{ name: 'denah' }];
  } else if (start === 'catat' || start === 'catat-keluar') {
    draft = newDraft();
    if (start === 'catat-keluar') {
      draft.mode = 'keluar';
      draft.amount = q.get('amount') || '';
    } else {
      draft.amount = q.get('amount') || '';
    }
    route = { name: 'catat' };
    stack = [{ name: 'denah' }];
  } else if (start === 'haul') {
    route = { name: 'haul' };
    stack = [{ name: 'denah' }];
  } else if (start === 'koreksi') {
    const id = ACCT_ORDER.includes(q.get('acct')) ? q.get('acct') : 'gopay';
    K2 = { acct: id, actual: q.get('actual') || '', room: lastRoomFor(id) };
    route = { name: 'koreksi' };
    stack = [{ name: 'denah' }];
  } else if (start === 'draf' || start === 'tangkap') {
    S.pro = true;
    if (start === 'draf') grantCapture();
    route = { name: start };
    stack = [{ name: start === 'draf' ? 'transaksi' : 'lainnya' }];
  } else if (SCREENS[start]) {
    route = { name: start };
  }
  render();
  if (start === 'pro') openSheet('pro');
  if (start === 'kilat') openKilat();
  if (start === 'notif') showNotif();
})();
