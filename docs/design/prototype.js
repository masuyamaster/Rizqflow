/*
 * Rizqflow — prototipe klik (S05, S06/S07, S11, S16, S21 + tab lain versi ringkas).
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
  };
  const icon = (name) => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>`;

  /* ----------------------------------------------------------------- data */

  const ORDER = ['memberi', 'diri', 'keluarga'];

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
      { date: '18 Sep', title: 'Belanja pasar', sub: 'Belanja bulanan · Keluarga', amt: -150000, room: 'keluarga' },
      { date: '17 Sep', title: 'Token listrik', sub: 'Listrik dan air · Keluarga', amt: -200000, room: 'keluarga' },
      { date: '15 Sep', title: 'Infak masjid', sub: 'Infak · Memberi', amt: -200000, room: 'memberi' },
      { date: '12 Sep', title: 'Reksa dana pasar uang', sub: 'Investasi · Diri', amt: -1148000, room: 'diri' },
      { date: '12 Sep', title: 'Dana darurat', sub: 'Dana darurat · Diri', amt: -1020000, room: 'diri' },
      { date: '8 Sep', title: 'Sedekah', sub: 'Sedekah · Memberi', amt: -100000, room: 'memberi' },
      { date: '1 Sep', title: 'Gaji September', sub: 'Pemasukan · dialirkan ke 3 ruang', amt: 8500000, room: null },
    ],
  });

  let S = seed();
  let prevView = null; // snapshot Denah sebelum alokasi, untuk animasi aliran
  let route = { name: 'denah' };
  let stack = [];
  let draft = newDraft();
  let A = null; // draf alokasi (S07)
  let undo = null;
  let toastTimer = null;

  function newDraft() {
    return { mode: 'masuk', amount: '', sumber: 'Freelance', room: 'keluarga', cat: null };
  }

  /* ------------------------------------------------------------- turunan */

  const rooms = () => ORDER.map((id) => S.rooms[id]);
  const pendingTotal = () => S.pending.reduce((a, p) => a + p.amount, 0);
  const allocTotal = () => rooms().reduce((a, r) => a + r.alloc, 0);
  const incomeTotal = () => allocTotal() + pendingTotal();
  const color = (r) => `var(--rf-room-${r.slot})`;

  /** Status hak terpenuhi menurut tipe ruang (usulan di docs/konsep.md, perlu konfirmasi). */
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
    const total = r.pos.reduce((a, p) => a + p.base, 0);
    let left = r.alloc;
    return r.pos.map((p, i) => {
      const last = i === r.pos.length - 1;
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
      if (!d.cat || !r.pos.some((p) => p.name === d.cat)) d.cat = r.pos[0].name;
      const roomChips = rooms()
        .map(
          (x) =>
            `<button type="button" class="chip-btn" aria-pressed="${d.room === x.id}" data-action="room-pick" data-arg="${x.id}"><span class="dot" style="--seg:${color(x)}"></span>${esc(x.name)}</button>`
        )
        .join('');
      const catChips = r.pos
        .map((p) => `<button type="button" class="chip-btn" aria-pressed="${d.cat === p.name}" data-action="cat-pick" data-arg="${esc(p.name)}">${esc(p.name)}</button>`)
        .join('');
      fields = `<p class="field-label">Ruang</p><div class="chips">${roomChips}</div>
        <p class="field-label">Kategori</p><div class="chips">${catChips}</div>`;
    }

    const rows = `<ul class="list field-rows">
      <li><button type="button" class="list-row" data-action="info" data-arg="Pemilih akun belum dibuat di prototipe.">
        <span class="list-row__main"><p class="list-row__sub">Akun</p><p class="list-row__title">Bank Jago</p></span>${icon('chevron')}</button></li>
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
    const groups = [];
    S.tx.forEach((t) => {
      let g = groups.find((x) => x.date === t.date);
      if (!g) groups.push((g = { date: t.date, items: [] }));
      g.items.push(t);
    });
    const html = groups
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
      .join('');
    return `<header class="topbar"><div><h1 tabindex="-1">Transaksi</h1>
      <p class="topbar__sub">Versi ringkas (S08): filter dan pencarian belum dibuat</p></div></header>${html}`;
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
    return `<header class="topbar"><div><h1 tabindex="-1">Lainnya</h1><p class="topbar__sub">Versi ringkas (S18)</p></div></header>
      <ul class="list">
        <li><button type="button" class="list-row" data-action="pro"><span class="attn-icon" style="color:var(--rf-primary)">${icon('star')}</span>
          <span class="list-row__main"><p class="list-row__title">Rizqflow Pro</p><p class="list-row__sub">Sekali bayar, tanpa langganan</p></span>${icon('chevron')}</button></li>
        ${row('Akun dan kategori', 'S13', 'S13 belum dibuat di prototipe.')}
        ${row('Aturan alokasi', 'S12', 'S12 belum dibuat di prototipe.')}
        ${row('Keamanan', 'PIN dan biometrik (S19), selalu gratis', 'S19 belum dibuat di prototipe.')}
        ${row('Backup, restore, dan ekspor', 'S20, selalu gratis', 'S20 belum dibuat di prototipe.')}
        ${row('Tentang dan disclaimer', 'S23', 'S23 belum dibuat di prototipe.')}
      </ul>`;
  }

  const SCREENS = {
    denah: denahScreen, catat: catatScreen, alokasi: alokasiScreen, detail: detailScreen,
    haul: haulScreen, transaksi: transaksiScreen, ruang: ruangScreen, lainnya: lainnyaScreen,
  };
  const NO_NAV = ['catat', 'alokasi', 'haul'];
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

  function openSheet() {
    $('#sheet').innerHTML = `<div class="sheet__grab"></div>
      <h2>Rizqflow Pro</h2>
      <ul class="benefits">
        <li>${icon('check')}<span>Ruang peran tak terbatas, lengkap dengan sistem per peran</span></li>
        <li>${icon('check')}<span>Aturan alokasi lanjutan: prioritas, batas atas, sisa mengalir</span></li>
        <li>${icon('check')}<span>Multi-profil zakat dengan pengingat haul</span></li>
      </ul>
      <p class="price">${rp(129000)} <span class="muted" style="font-weight:500">sekali bayar</span></p>
      <p class="list-row__sub" style="margin:2px 0 var(--rf-space-4)">Tanpa langganan. Harga hanya contoh.</p>
      <div class="actions">
        <button type="button" class="btn btn--primary btn--block" data-action="buy">Beli</button>
        <button type="button" class="btn btn--text btn--block" data-action="info" data-arg="Pulihkan pembelian: simulasi, tidak ada akun toko.">Pulihkan pembelian</button>
        <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Nanti saja</button>
      </div>
      <p class="disclaimer" style="text-align:center">Fitur dasar dan zakat tetap gratis.</p>`;
    $('#scrim').hidden = false;
    $('#sheet').hidden = false;
    const first = $('.btn--primary', $('#sheet'));
    if (first) first.focus();
  }

  function closeSheet() {
    $('#scrim').hidden = true;
    $('#sheet').hidden = true;
  }

  function startAlokasi(label, amount, pendingId) {
    A = {
      label, amount, pendingId,
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
    S.tx.unshift({ date: 'Hari ini', title: A.label, sub: `Pemasukan · dialirkan ke ${ids.length} ruang`, amt: A.amount, room: null });
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
    r.used += n;
    const p = r.pos.find((x) => x.name === draft.cat);
    if (p) p.used += n;
    S.tx.unshift({ date: 'Hari ini', title: draft.cat, sub: `${draft.cat} · ${r.name}`, amt: -n, room: r.id });
    undo = () => {
      S = JSON.parse(before);
      render();
    };
    if (keepOpen) {
      draft.amount = '';
      updateCatat();
    } else {
      draft = newDraft();
      stack = [];
      route = { name: 'denah' };
      render();
    }
    toast(`${rp(n)} tersimpan di ${r.name}`, 'Urungkan');
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
      let a = draft.amount;
      if (arg === 'back') a = a.slice(0, -1);
      else if (arg === '000') a = a && a.length <= 8 ? a + '000' : a;
      else if (!(a === '' && arg === '0') && a.length < 11) a += arg;
      draft.amount = a;
      updateCatat();
    },
    preview: () => {
      const n = Number(draft.amount || 0);
      if (n) startAlokasi(draft.sumber === 'Gaji' ? 'Gaji' : draft.sumber, n, null);
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
    pro: () => openSheet(),
    'close-sheet': () => closeSheet(),
    buy: () => {
      closeSheet();
      toast('Simulasi: tidak ada pembayaran sungguhan di prototipe.');
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
    if (e.key === 'Escape' && !$('#sheet').hidden) closeSheet();
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

  const start = q.get('screen') || 'denah';
  if (start.startsWith('detail-') && S.rooms[start.slice(7)]) {
    route = { name: 'detail', id: start.slice(7), tab: 'denah' };
    stack = [{ name: 'denah' }];
  } else if (start === 'alokasi') {
    const p = S.pending[0];
    A = { label: p.label, amount: p.amount, pendingId: p.id, edit: q.get('edit') === '1', pcts: Object.fromEntries(ORDER.map((id) => [id, S.rooms[id].pct])) };
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
  } else if (SCREENS[start]) {
    route = { name: start };
  }
  render();
  if (start === 'pro') openSheet();
})();
