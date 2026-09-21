/*
 * Rizqflow — prototipe klik seluruh layar S01-S28 (data contoh, tanpa penyimpanan).
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
    sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
    lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    swap: '<path d="M7 7h12M15 3l4 4-4 4"/><path d="M17 17H5M9 13l-4 4 4 4"/>',
  };
  const icon = (name) => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICON[name]}</svg>`;

  /* ----------------------------------------------------------------- data */

  const ORDER = ['memberi', 'diri', 'keluarga'];
  const ACCT_ORDER = ['tunai', 'jago', 'gopay'];

  const seed = () => ({
    // Ruang dan akun yang dipakai, berurutan. Data contoh memakai tiga ruang dan tiga akun.
    order: ORDER.slice(),
    acctOrder: ACCT_ORDER.slice(),
    demo: true, // data contoh (mode demo), bukan data pengguna
    zakat: true, // profil harta zakat sudah diisi
    archived: [], // ruang yang diarsipkan
    zk: seedZk(),
    sec: { pinOn: false, pin: '', bio: false, lock: 'segera', hide: true },
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
    // Jumlah (amt) bertanda: pengeluaran negatif, pemasukan positif. Transfer selalu positif dan punya `to`.
    tx: [
      { id: 't1', kind: 'expense', date: '18 Sep', title: 'Belanja pasar', sub: 'Belanja bulanan · Keluarga', amt: -150000, room: 'keluarga', cat: 'Belanja bulanan', acct: 'jago', note: 'Belanja pasar' },
      { id: 't2', kind: 'expense', date: '17 Sep', title: 'Token listrik', sub: 'Listrik dan air · Keluarga', amt: -200000, room: 'keluarga', cat: 'Listrik dan air', acct: 'jago', note: 'Token listrik' },
      { id: 't3', kind: 'expense', date: '15 Sep', title: 'Infak masjid', sub: 'Infak · Memberi', amt: -200000, room: 'memberi', cat: 'Infak', acct: 'tunai', note: 'Infak masjid' },
      { id: 't4', kind: 'expense', date: '12 Sep', title: 'Reksa dana pasar uang', sub: 'Investasi · Diri', amt: -1148000, room: 'diri', cat: 'Investasi', acct: 'jago', note: 'Reksa dana pasar uang' },
      { id: 't5', kind: 'expense', date: '12 Sep', title: 'Dana darurat', sub: 'Dana darurat · Diri', amt: -1020000, room: 'diri', cat: 'Dana darurat', acct: 'jago', note: 'Dana darurat' },
      { id: 't6', kind: 'expense', date: '8 Sep', title: 'Sedekah', sub: 'Sedekah · Memberi', amt: -100000, room: 'memberi', cat: 'Sedekah', acct: 'tunai', note: 'Sedekah' },
      {
        id: 't7', kind: 'income', date: '1 Sep', title: 'Gaji September', sub: 'Pemasukan · dialirkan ke 3 ruang', amt: 8500000, room: null, acct: 'jago',
        source: 'Gaji', note: 'Gaji September', pcts: { memberi: 10, diri: 30, keluarga: 60 }, alloc: { memberi: 850000, diri: 2550000, keluarga: 5100000 },
      },
    ],
    // Akun: "days" = hari sejak terakhir dicocokkan (Koreksi saldo, S25).
    acct: {
      tunai: { id: 'tunai', name: 'Tunai', kind: 'tunai', bal: 312000, days: 3 },
      jago: { id: 'jago', name: 'Bank Jago', kind: 'bank', bal: 6840000, days: 2 },
      gopay: { id: 'gopay', name: 'GoPay', kind: 'ewallet', bal: 185000, days: 12 },
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

  // Pola "Tiga hak" (S02): ruang bawaan untuk onboarding.
  const TEMPLATE_IDS = ['memberi', 'diri', 'keluarga'];
  const TEMPLATE = seed().rooms;
  const TEMPLATE_DESC = {
    memberi: 'Sedekah, infak, dan zakat',
    diri: 'Dana darurat, investasi, belajar',
    keluarga: 'Belanja, listrik dan air, sekolah',
  };
  const RULE_SAMPLE = 1000000; // nominal contoh di penyunting persentase (S03, S12)

  /** Data awal pengguna baru setelah onboarding: ruang kosong, satu akun, tanpa transaksi. */
  function freshState(ob) {
    const base = seed();
    const order = ob.template === 'tiga' ? TEMPLATE_IDS.slice() : [];
    const rooms = {};
    order.forEach((id) => {
      const r = base.rooms[id];
      r.pct = ob.pcts[id];
      r.alloc = 0;
      r.used = 0;
      r.pos.forEach((p) => {
        p.used = 0;
      });
      rooms[id] = r;
    });
    const acctId = 'akun1';
    return Object.assign(base, {
      order, rooms, demo: false, zakat: false, zk: emptyZk(), pending: [], tx: [], favs: [], checked: {}, drafts: [], sampleIdx: 0,
      acct: { [acctId]: { id: acctId, name: ob.acctName.trim(), kind: ob.acctType, bal: Number(ob.balance || 0), days: 0 } },
      acctOrder: [acctId], lastAcct: acctId,
      last: { room: order.includes('keluarga') ? 'keluarga' : null, cat: order.includes('keluarga') ? 'Lain-lain' : null },
    });
  }

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
  let OB = null; // draf onboarding (S01-S04, flow F1)
  let R = null; // penyunting persentase: S03 (onboarding) dan S12 (aturan alokasi, flow F4)
  let realState = null; // data pengguna yang disimpan selama mode demo (S22)
  let H = null; // draf profil harta (S15)
  let U = null; // draf tunaikan zakat (S17)
  let TD = null; // draf detail transaksi (S09)
  let SH = {}; // isian sheet (ruang baru, akun baru, kategori baru)
  let PIN = null; // pembuatan PIN (S19)
  let LOCK = null; // simulasi kunci aplikasi (S19)
  let IM = { picked: false }; // impor CSV (S20)
  let RS = null; // pemulihan cadangan (S20)
  let LANG = 'id';
  let backupStore = null; // cadangan simulasi: sandi dan salinan data
  let txSeq = 100;
  const newTxId = () => 't' + ++txSeq;

  function newDraft() {
    return {
      mode: 'masuk', amount: '', sumber: S.demo ? 'Freelance' : 'Gaji',
      room: S.rooms.keluarga ? 'keluarga' : S.order[0] || null, cat: null, acct: defAcct(), fav: false, fromDraft: null, from: defAcct(), to: otherAcct(defAcct()),
    };
  }

  /** Akun bawaan untuk pemasukan: Bank Jago di data contoh, akun pertama di data pengguna. */
  function defAcct() {
    return S.acctOrder.includes('jago') ? 'jago' : S.acctOrder[0];
  }

  /* ------------------------------------------------------------- turunan */

  const rooms = () => S.order.map((id) => S.rooms[id]);
  const pendingTotal = () => S.pending.reduce((a, p) => a + p.amount, 0);
  const allocTotal = () => rooms().reduce((a, r) => a + r.alloc, 0);
  const incomeTotal = () => allocTotal() + pendingTotal();
  const color = (r) => (r.slot > 3 ? 'var(--rf-primary)' : `var(--rf-room-${r.slot})`); // ruang ke-4 dst: warna netral
  const accts = () => S.acctOrder.map((id) => S.acct[id]);
  const catsOf = (r) => r.pos.filter((p) => p.base > 0);

  /** Ruang dari pengeluaran terakhir di sebuah akun; cadangan: ruang bertipe Mencukupi (Keluarga). */
  function lastRoomFor(acctId) {
    const t = S.tx.find((x) => x.acct === acctId && x.amt < 0 && x.room);
    return t ? t.room : S.rooms.keluarga ? 'keluarga' : S.order[0] || null;
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
    S.tx.unshift({ id: newTxId(), kind: 'expense', date: 'Hari ini', title: title || cat, sub: `${cat} · ${r.name}`, amt: -n, room: r.id, cat, acct, note: title || cat });
  }

  /** Status hak terpenuhi menurut tipe ruang (dikonfirmasi 2026-09-20, lihat docs/konsep.md). */
  function status(r) {
    // Ruang baru tanpa jatah tampil netral (bukan Perlu perhatian) sampai ada pemasukan.
    if (!r.alloc) return { key: 'idle', label: 'Belum ada jatah', ratio: 0 };
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

  const STATUS_ICON = { good: 'check', progress: 'clock', attn: 'alert', idle: 'clock' };
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

  /**
   * Membagi nominal ke ruang. Pembulatan: metode sisa terbesar, urutan prioritas ruang memutus seri
   * (keputusan 2026-09-21; sama dengan AllocationEngine di :domain).
   */
  function split(amount, pcts, ids = S.order) {
    if (!ids.length) return { parts: {}, total: 0, rest: amount, valid: true };
    const total = ids.reduce((a, id) => a + pcts[id], 0);
    const exact = ids.map((id) => amount * pcts[id]); // pembilang per 100
    const parts = exact.map((x) => Math.floor(x / 100));
    const valid = total <= 100;
    let rest = 0;
    if (valid) {
      const target = Math.floor((amount * total) / 100);
      let left = target - parts.reduce((a, b) => a + b, 0);
      const byFraction = ids.map((_, i) => i).sort((a, b) => (exact[b] % 100) - (exact[a] % 100) || a - b);
      for (const i of byFraction) {
        if (left <= 0 || exact[i] % 100 === 0) break;
        parts[i] += 1;
        left -= 1;
      }
      rest = amount - target;
    }
    return { parts: Object.fromEntries(ids.map((id, i) => [id, parts[i]])), total, rest, valid };
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
            <span class="metric">${r.alloc ? `<b>${rpShort(r.used)}</b>${r.verb} dari ${rpShort(r.alloc)}` : `<b>Rp${NBSP}0</b>Menunggu rezeki`}</span></span>
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
    if (S.zakat) {
      attn.push({ icon: 'clock', tone: 'info', text: 'Nilai harta zakat terakhir diperbarui 40 hari lalu', action: ['Lihat', 'open-zakat'] });
    }
    const attnHtml = attn
      .slice(0, 3)
      .map(
        (a) => `<li><div class="list-row"><span class="attn-icon ${a.tone ? 'attn-icon--info' : ''}">${icon(a.icon)}</span>
        <span class="list-row__main">${esc(a.text)}</span>
        <button type="button" class="btn btn--text btn--small" data-action="${a.action[1]}" data-arg="${a.action[2] || ''}">${a.action[0]}</button></div></li>`
      )
      .join('');

    // Denah tanpa data: satu kalimat dan dua ajakan (catat rezeki pertama, coba data contoh).
    const emptyCard = `<section class="card empty-card" aria-label="Belum ada rezeki">
        <p class="empty-card__title">Belum ada rezeki bulan ini</p>
        <p class="list-row__sub">Catat rezeki pertamamu, lalu lihat ia mengalir ke ruang-ruangmu.</p>
        <div class="actions" style="margin-top:var(--rf-space-4)">
          <button type="button" class="btn btn--primary btn--block" data-action="catat">Catat rezeki pertama</button>
          <button type="button" class="btn btn--text btn--block" data-action="enter-demo">Coba data contoh</button></div></section>`;
    const noRooms = `<div class="card empty-card"><p class="empty-card__title">Belum ada ruang</p>
        <p class="list-row__sub">Rezeki yang masuk akan menunggu sampai ada ruang untuk dialirkan.</p>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--tonal btn--block" data-action="apply-template">Pakai pola Tiga hak</button></div></div>`;
    return `<header class="topbar"><div><h1 tabindex="-1">Denah</h1>
        <p class="topbar__sub">September 2026${S.zakat ? ' · ±' + hijriStr(TODAY) : ''}</p></div>${
          S.demo ? '<button type="button" class="badge badge--btn" data-action="demo-info">Mode demo</button>' : ''
        }</header>
      <section class="card card--hero" aria-label="Ringkasan rezeki bulan ini">
        <p class="hero-label">Rezeki bulan ini</p>
        <p class="hero-number">${rp(total)}</p>
        ${total ? stackBar(items, pend, aria) + legend(items, total, pend) : ''}
        ${
          pend
            ? `<div class="pending"><span>${icon('alert')}</span><span class="pending__text">${rp(pend)} belum dialirkan</span>
               <button type="button" class="btn btn--tonal btn--small" data-action="alirkan-pending">Alirkan</button></div>`
            : ''
        }
      </section>
      ${total ? '' : emptyCard}
      <h2 class="section-title">Ruang</h2>
      ${rooms().length ? '' : noRooms}
      <div class="rooms-grid">${cards}
        <button type="button" class="room-card room-card--add" data-action="tambah-ruang">${icon('plus')}Ruang baru</button>
      </div>
      <h2 class="section-title">Perlu perhatian</h2>
      ${attnHtml ? `<ul class="list">${attnHtml}</ul>` : '<p class="empty">Tidak ada yang perlu diperhatikan sekarang.</p>'}`;
  }

  function catatScreen() {
    const d = draft;
    const tabs = `<div class="seg-control" role="tablist" aria-label="Jenis transaksi">
      <button type="button" role="tab" aria-selected="${d.mode === 'masuk'}" data-action="mode" data-arg="masuk">Pemasukan</button>
      <button type="button" role="tab" aria-selected="${d.mode === 'keluar'}" data-action="mode" data-arg="keluar">Pengeluaran</button>
      <button type="button" role="tab" aria-selected="${d.mode === 'transfer'}" data-action="mode" data-arg="transfer">Transfer</button></div>`;

    let fields = '';
    if (d.mode === 'masuk') {
      const chips = ['Gaji', 'Usaha', 'Freelance', 'Lainnya']
        .map((s) => `<button type="button" class="chip-btn" aria-pressed="${d.sumber === s}" data-action="sumber" data-arg="${s}">${s}</button>`)
        .join('');
      fields = `<p class="field-label">Sumber</p><div class="chips">${chips}</div>`;
    } else if (d.mode === 'transfer') {
      fields = transferFields(d);
    } else if (!rooms().length) {
      fields = `<div class="banner banner--info">${icon('clock')}<span>Belum ada ruang untuk pengeluaran. Pakai pola Tiga hak, atau tambah ruang di tab Ruang.</span></div>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--tonal btn--block" data-action="apply-template">Pakai pola Tiga hak</button></div>`;
    } else {
      if (!S.order.includes(d.room)) d.room = S.order[0];
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
        : d.mode === 'transfer'
          ? `<button type="button" class="btn btn--primary btn--block" id="btn-primary" data-action="save-transfer">Simpan</button>`
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
    const noRoom = d.mode === 'keluar' && !rooms().length;
    const badTransfer = d.mode === 'transfer' && (!d.to || d.to === d.from);
    $('#btn-primary').disabled = !n || noRoom || badTransfer;
    const add = $('#btn-add');
    if (add) add.disabled = !n || noRoom;
    const banner = $('#banner');
    if (d.mode === 'keluar' && !noRoom) {
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
      ${
        rooms().length
          ? `<div class="switch-row"><div><b>Ubah sekali ini</b><p class="list-row__sub">${A.edit ? 'Hanya untuk pemasukan ini. Aturan tetap tidak berubah.' : 'Sesuaikan pembagian tanpa mengubah aturan.'}</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${A.edit}" aria-label="Ubah sekali ini" data-action="toggle-edit"></button></div>`
          : `<div class="banner banner--info">${icon('clock')}<span>Belum ada ruang. Rezeki ini tersimpan sebagai belum dialirkan sampai kamu menambah ruang.</span></div>`
      }
      <button type="button" class="btn btn--primary btn--block" data-action="confirm-alokasi" ${valid ? '' : 'disabled'}>${rooms().length ? 'Alirkan' : 'Simpan, alirkan nanti'}</button>`;
  }

  function detailScreen() {
    const r = S.rooms[route.id];
    const st = status(r);
    const to = Math.min(1, st.ratio);
    const zakat = r.id === 'memberi' ? zakatCard(r) : '';
    const pos = posList(r)
      .map((p) => {
        const ratio = p.alloc ? p.used / p.alloc : 0;
        const over = p.used - p.alloc;
        if (!p.alloc) {
          return `<li><div class="pos" style="--seg:${color(r)}"><div class="pos__top"><span>${esc(p.name)}</span><span>${rp(p.used)}</span></div>
            <p class="pos__sub">${r.alloc ? `Tanpa jatah tersendiri. Ikut dihitung dalam jatah ${esc(r.name)}.` : 'Belum ada jatah bulan ini. Alirkan rezeki dulu.'}</p></div></li>`;
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
      <div style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="open-aturan" data-arg="${r.id}">Atur aturan ${esc(r.name)}</button>
        <button type="button" class="btn btn--text btn--block" data-action="arsip-ruang" data-arg="${r.id}">Arsipkan ruang</button></div>`;
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
      ${
        rows
          ? `<ul class="list">${rows}</ul>
      <h2 class="section-title">Aturan alokasi</h2>
      <ul class="list"><li><button type="button" class="list-row" data-action="open-aturan"><span class="attn-icon" style="color:var(--rf-primary)">${icon('sliders')}</span>
        <span class="list-row__main"><p class="list-row__title">Pembagian rezeki</p><p class="list-row__sub">${rooms().map((r) => `${esc(r.name)} ${r.pct}%`).join(' · ')}</p></span>${icon('chevron')}</button></li></ul>`
          : `<div class="card empty-card"><p class="empty-card__title">Belum ada ruang</p>
        <p class="list-row__sub">Ruang adalah tempat rezeki dialirkan, misalnya Memberi, Diri, dan Keluarga.</p>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--tonal btn--block" data-action="apply-template">Pakai pola Tiga hak</button></div></div>`
      }
      <div style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="tambah-ruang">${icon('plus')}Tambah ruang</button>
      <p class="list-row__sub" style="text-align:center;margin-top:8px">${rooms().length} dari 5 ruang gratis</p></div>
      ${
        S.archived.length
          ? `<ul class="list" style="margin-top:var(--rf-space-4)"><li><button type="button" class="list-row" data-action="open-arsip"><span class="list-row__main"><p class="list-row__title">Diarsipkan (${S.archived.length})</p></span>${icon('chevron')}</button></li></ul>`
          : ''
      }`;
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
      `<div class="switch-row" style="margin:var(--rf-space-3) 0"><div><b>${esc(label)}</b><p class="list-row__sub">Dipetakan ke akun ${esc((S.acct[id] || S.acct[S.acctOrder[0]]).name)}</p></div>
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

  /* --------------------------------------------- data zakat (S14-S17) */

  const TODAY = new Date(2026, 8, 20); // tanggal tetap prototipe: 20 September 2026
  const HIJRI_MONTHS = [
    'Muharram', 'Safar', 'Rabiul Awal', 'Rabiul Akhir', 'Jumadil Awal', 'Jumadil Akhir',
    'Rajab', 'Syaban', 'Ramadhan', 'Syawal', "Dzulqa'dah", 'Dzulhijjah',
  ];
  const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  const hijriStr = (d) => {
    const parts = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', { day: 'numeric', month: 'numeric', year: 'numeric' }).formatToParts(d);
    const get = (t) => parseInt(parts.find((p) => p.type === t).value, 10);
    return `${get('day')} ${HIJRI_MONTHS[get('month') - 1]} ${get('year')}`;
  };
  const masehiStr = (d) => d.toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' });

  function seedZk() {
    return {
      mode: 'zakat', started: true, state: 'running', elapsed: 117, total: 354,
      price: 1660000, priceDate: '12 Sep 2026',
      items: [
        { id: 'z1', label: 'Emas', kind: 'gold', grams: 60, value: 0 },
        { id: 'z2', label: 'Tabungan dan deposito', kind: 'cash', value: 38000000 },
        { id: 'z3', label: 'Reksa dana', kind: 'invest', value: 25000000 },
        { id: 'z4', label: 'Piutang lancar', kind: 'recv', value: 4000000 },
        { id: 'z5', label: 'Hutang jatuh tempo', kind: 'deduct', value: 14600000 },
      ],
      payments: [],
    };
  }

  function emptyZk() {
    return { mode: 'zakat', started: false, state: 'running', elapsed: 0, total: 354, price: 0, priceDate: '', items: [], payments: [] };
  }

  const zkValue = (Z, it) => (it.kind === 'gold' ? it.grams * Z.price : it.value);
  const zkNet = (Z) => Z.items.reduce((a, it) => a + (it.kind === 'deduct' ? -zkValue(Z, it) : zkValue(Z, it)), 0);
  const zkNisab = (Z) => 85 * Z.price;
  const zkDue = (Z) => Math.max(0, Math.round((zkNet(Z) * 250) / 10000)); // 2,5%, setengah ke atas

  /** setup: profil belum diisi; below: di bawah nisab; running: haul berjalan; done: haul genap. */
  function zkStatus(Z) {
    if (!Z.started || !Z.price) return 'setup';
    if (zkNet(Z) < zkNisab(Z)) return 'below';
    return Z.state === 'done' ? 'done' : 'running';
  }

  function zkDates(Z) {
    const start = addDays(TODAY, -Z.elapsed);
    return { start, due: addDays(start, Z.total) };
  }

  const zkChip = (st) =>
    ({
      setup: `<span class="chip chip--idle">${icon('clock')}Belum diisi</span>`,
      below: `<span class="chip chip--progress">${icon('clock')}Belum mencapai nisab</span>`,
      running: `<span class="chip chip--progress">${icon('clock')}Haul berjalan</span>`,
      done: `<span class="chip chip--good">${icon('check')}Haul genap</span>`,
    })[st];

  /** Kartu Zakat di S11 ruang Memberi. */
  function zakatCard(r) {
    const Z = S.zk;
    const st = zkStatus(Z);
    const sub = {
      setup: 'Mulai dengan mengisi harta',
      below: 'Belum mencapai nisab · dipantau',
      running: `Haul berjalan · hari ke-${Z.elapsed} dari ${Z.total}`,
      done: `Haul genap · zakat ${rp(zkDue(Z))}`,
    }[st];
    const ratio = st === 'running' || st === 'done' ? Math.min(1, Z.elapsed / Z.total) : 0;
    return `<button type="button" class="zakat-card" style="--seg:${color(r)}" data-action="open-zakat">
      ${ring(ratio, ratio, r, `${pct(ratio)}%`)}
      <span class="list-row__main"><span class="list-row__title">Zakat mal</span><p class="list-row__sub">${sub}</p></span>${icon('chevron')}</button>`;
  }

  /** S14 Beranda Zakat. */
  function zakatScreen() {
    const Z = S.zk;
    const r = S.rooms.memberi || { slot: 1, verb: 'Tersalur', name: 'Memberi' };
    const st = zkStatus(Z);
    const d = zkDates(Z);
    const hist = Z.payments.length
      ? Z.payments.map((p) => `<li><div class="list-row"><span class="list-row__main"><p class="list-row__title">${esc(p.date)}</p><p class="list-row__sub">${esc(p.hijri)}</p></span><span class="list-row__end">${rp(p.amt)}</span></div></li>`).join('')
      : `<li><p class="empty">Belum ada zakat yang ditunaikan.</p></li>`;
    let hero;
    if (Z.mode === 'percent') {
      hero = `<div class="card"><p class="hero-label">Mode persentase donasi</p>
        <p class="list-row__sub" style="margin:4px 0 0">Tanpa nisab dan haul. Jatah donasi bulan ini mengikuti ruang Memberi.</p>
        <p class="hero-number hero-number--sm">${rp(S.rooms.memberi ? S.rooms.memberi.alloc : 0)}</p></div>`;
    } else if (st === 'setup') {
      hero = `<div class="card empty-card"><p class="empty-card__title">Mulai dengan mengisi harta</p>
        <p class="list-row__sub">Isi harga emas dan hartamu. Rizqflow menghitung nisab dan memantau haul.</p>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--primary btn--block" data-action="open-harta">Isi profil harta</button></div></div>`;
    } else {
      const ratio = st === 'below' ? 0 : Math.min(1, Z.elapsed / Z.total);
      const big =
        st === 'below'
          ? `<span>${rp(zkNet(Z))}<small>harta bersih</small></span>`
          : `<span>${Z.elapsed}<small>dari ${Z.total} hari</small></span>`;
      const line =
        st === 'below'
          ? 'Dipantau, belum ada haul. Haul mulai saat harta mencapai nisab.'
          : st === 'running'
            ? `Jatuh tempo ${hijriStr(d.due)} (±${masehiStr(d.due)})`
            : `Zakat yang perlu ditunaikan ${rp(zkDue(Z))}`;
      hero = `<section class="haul-hero" style="--seg:${color(r)}" aria-label="Status zakat">
        ${ring(ratio, ratio, r, big)}${zkChip(st)}<p class="list-row__sub" style="margin:0;text-align:center">${line}</p></section>
        ${st === 'done' ? `<div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="open-tunai">Tunaikan zakat</button></div>` : ''}`;
    }
    const kv =
      Z.mode === 'zakat' && st !== 'setup'
        ? `<div class="card" style="margin-top:var(--rf-space-3)"><dl class="kv">
            <div><dt>Harta bersih</dt><dd>${rp(zkNet(Z))}</dd></div>
            <div><dt>Nisab hari ini</dt><dd>${rp(zkNisab(Z))}<br /><span class="muted">85 g × ${rp(Z.price)} per g</span></dd></div>
            <div><dt>Harga emas</dt><dd><span class="muted">Input manual, diperbarui ${esc(Z.priceDate)}</span></dd></div></dl></div>
          <div class="actions" style="margin-top:var(--rf-space-3)">
            <button type="button" class="btn btn--tonal btn--block" data-action="open-haul">Lihat rincian</button>
            <button type="button" class="btn btn--tonal btn--block" data-action="open-harta">Perbarui nilai harta</button></div>`
        : '';
    return `${topbarNav('Zakat')}
      ${hero}${kv}
      <h2 class="section-title">Riwayat zakat</h2><ul class="list">${hist}</ul>
      <div class="switch-row"><div><b>Persentase donasi biasa</b><p class="list-row__sub">Ganti ke mode tanpa nisab dan haul. Data zakatmu tidak dihapus.</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${Z.mode === 'percent'}" aria-label="Persentase donasi biasa" data-action="toggle-giving"></button></div>
      <div class="card sim-card"><p class="list-row__sub" style="margin:0 0 var(--rf-space-2)"><b>Hanya di prototipe:</b> percepat haul untuk mencoba alur Tunaikan zakat.</p>
        <div class="chips"><button type="button" class="chip-btn" data-action="zk-sim" data-arg="done">Haul genap</button>
        <button type="button" class="chip-btn" data-action="zk-sim" data-arg="running">Haul berjalan</button></div></div>
      <p class="disclaimer">Asumsi: nisab 85 gram emas, tarif 2,5%, haul 1 tahun Hijriyah (Umm al-Qura, bisa berbeda satu hari dari penetapan Kemenag). Bantuan hitung, bukan fatwa.</p>`;
  }

  /** S16 Kartu haul dan rincian. */
  function haulScreen() {
    const Z = S.zk;
    const r = S.rooms.memberi || { slot: 1, verb: 'Tersalur', name: 'Memberi' };
    const st = zkStatus(Z);
    const d = zkDates(Z);
    const running = st === 'running' || st === 'done';
    const ratio = running ? Math.min(1, Z.elapsed / Z.total) : 0;
    const items = Z.items
      .map((it) => {
        const label = it.kind === 'gold' ? `${esc(it.label)} ${it.grams} g` : esc(it.label);
        const v = zkValue(Z, it);
        return `<div><dt>${label}</dt><dd>${rp(it.kind === 'deduct' ? -v : v)}</dd></div>`;
      })
      .join('');
    return `${topbarNav('Zakat mal')}
      <section class="haul-hero" style="--seg:${color(r)}" aria-label="Progres haul">
        ${ring(ratio, ratio, r, running ? `<span>${Z.elapsed}<small>dari ${Z.total} hari</small></span>` : '<span>0<small>haul belum mulai</small></span>')}
        ${zkChip(st)}
      </section>
      ${
        running
          ? `<div class="card"><dl class="kv">
        <div><dt>Mulai</dt><dd>${hijriStr(d.start)}<br /><span class="muted">±${masehiStr(d.start)}</span></dd></div>
        <div><dt>Jatuh tempo</dt><dd>${hijriStr(d.due)}<br /><span class="muted">±${masehiStr(d.due)}</span></dd></div>
        <div><dt>Sisa</dt><dd>${Math.max(0, Z.total - Z.elapsed)} hari</dd></div></dl></div>`
          : `<p class="list-row__sub">Haul dimulai saat harta bersih mencapai nisab. Belum ada tanggal mulai.</p>`
      }
      <h2 class="section-title">Nisab</h2>
      <div class="card"><dl class="kv">
        <div><dt>Harta bersih</dt><dd>${rp(zkNet(Z))}</dd></div>
        <div><dt>Nisab hari ini</dt><dd>${rp(zkNisab(Z))}<br /><span class="muted">85 g × ${rp(Z.price)} per g</span></dd></div>
        <div><dt>Status</dt><dd>${zkNet(Z) >= zkNisab(Z) ? `<span class="chip chip--good">${icon('check')}Di atas nisab</span>` : `<span class="chip chip--progress">${icon('clock')}Di bawah nisab</span>`}</dd></div>
        <div class="kv--total"><dt>Perkiraan zakat (2,5%)</dt><dd>${rp(zkDue(Z))}</dd></div></dl></div>
      <details class="card" style="margin-top:var(--rf-space-3)"><summary>Lihat rincian perhitungan</summary>
        <dl class="kv">${items}<div class="kv--total"><dt>Harta bersih</dt><dd>${rp(zkNet(Z))}</dd></div></dl></details>
      <p class="disclaimer">Asumsi: nisab 85 gram emas, tarif 2,5%, haul 1 tahun Hijriyah (Umm al-Qura, bisa berbeda satu hari dari penetapan Kemenag). Harga emas dan angka lain hanya contoh. Ini bantuan hitung, bukan fatwa.</p>
      <div class="actions" style="margin-top:var(--rf-space-4)">
        <button type="button" class="btn btn--tonal btn--block" data-action="open-harta">Perbarui nilai harta</button>
        <button type="button" class="btn btn--primary btn--block" data-action="open-tunai" ${st === 'done' ? '' : 'disabled'}>${st === 'done' ? 'Tunaikan zakat' : 'Tunaikan zakat (aktif saat haul genap)'}</button></div>`;
  }

  /** S15 Profil harta: draf H diedit, baru disimpan ke S.zk. */
  function hartaScreen() {
    const line = (it) => {
      const del = `<button type="button" class="icon-btn" data-action="harta-del" data-arg="${it.id}" aria-label="Hapus ${esc(it.label)}">${icon('close')}</button>`;
      const label = `<input class="field-input" type="text" data-hf="${it.id}:label" value="${esc(it.label)}" aria-label="Nama harta" />`;
      const val =
        it.kind === 'gold'
          ? `<input class="field-input" type="text" inputmode="numeric" data-hf="${it.id}:grams" value="${it.grams}" aria-label="Berat emas dalam gram" /><p class="list-row__sub" style="margin:4px 0 0" id="hv-${it.id}"></p>`
          : `<input class="field-input" type="text" inputmode="numeric" data-hf="${it.id}:value" value="${it.value}" aria-label="Nilai ${esc(it.label)} dalam rupiah" /><p class="list-row__sub" style="margin:4px 0 0" id="hv-${it.id}"></p>`;
      return `<div class="harta-row"><div class="harta-row__head">${label}${del}</div>${val}</div>`;
    };
    const assets = H.items.filter((i) => i.kind !== 'deduct').map(line).join('');
    const deds = H.items.filter((i) => i.kind === 'deduct').map(line).join('');
    return `${topbarNav('Profil harta')}
      <p class="field-label"><label for="hf-price">Harga emas per gram</label></p>
      <input id="hf-price" class="field-input" type="text" inputmode="numeric" data-hf="price" value="${H.price}" />
      <p class="list-row__sub" style="margin:4px 0 0" id="hp-note">Input manual. Harga otomatis ada di Pro.</p>
      <h2 class="section-title">Harta</h2>${assets || '<p class="empty">Belum ada harta. Tambahkan emas, tabungan, atau lainnya.</p>'}
      <div class="actions"><button type="button" class="btn btn--tonal btn--block" data-action="harta-add" data-arg="asset">${icon('plus')}Tambah harta</button>
        <button type="button" class="btn btn--text btn--block" data-action="harta-add" data-arg="gold">${icon('plus')}Tambah emas</button></div>
      <h2 class="section-title">Pengurang</h2>${deds || '<p class="list-row__sub">Belum ada pengurang.</p>'}
      <div class="actions" style="margin-top:var(--rf-space-2)"><button type="button" class="btn btn--text btn--block" data-action="harta-add" data-arg="deduct">${icon('plus')}Tambah pengurang</button></div>
      <div class="card" style="margin-top:var(--rf-space-4)"><dl class="kv">
        <div class="kv--total"><dt>Harta bersih</dt><dd id="hs-net">–</dd></div>
        <div><dt>Nisab hari ini</dt><dd id="hs-nisab">–</dd></div>
        <div><dt>Status</dt><dd id="hs-status">–</dd></div></dl></div>
      <p class="disclaimer">Setiap perubahan nilai dicatat bertanggal, karena haul bergantung pada kapan harta mencapai nisab.</p>
      <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--primary btn--block" data-action="harta-save">Simpan</button></div>`;
  }

  function hartaDraftZk() {
    return { price: Number(H.price || 0), items: H.items.map((i) => ({ ...i, grams: Number(i.grams || 0), value: Number(i.value || 0) })) };
  }

  function updateHarta() {
    const Z = hartaDraftZk();
    const note = $('#hp-note');
    if (note) note.textContent = Z.price ? `${rp(Z.price)} per gram. Input manual; harga otomatis ada di Pro.` : 'Isi harga emas supaya nisab bisa dihitung.';
    H.items.forEach((it) => {
      const el = $('#hv-' + it.id);
      if (!el) return;
      const z = Z.items.find((x) => x.id === it.id);
      el.textContent = it.kind === 'gold' ? `Bernilai ${rp(zkValue(Z, z))}` : rp(z.value);
    });
    const net = zkNet(Z);
    const nisab = zkNisab(Z);
    $('#hs-net').textContent = rp(net);
    $('#hs-nisab').textContent = Z.price ? rp(nisab) : '–';
    $('#hs-status').innerHTML = !Z.price
      ? `<span class="chip chip--idle">${icon('clock')}Isi harga emas</span>`
      : net >= nisab
        ? `<span class="chip chip--good">${icon('check')}Di atas nisab</span>`
        : `<span class="chip chip--progress">${icon('clock')}Di bawah nisab</span>`;
  }

  /** S17 Tunaikan zakat. */
  function tunaiScreen() {
    const r = S.rooms.memberi;
    const Z = S.zk;
    const acctList = acctChips(U.acct, 'u-acct');
    return `${topbarNav('Tunaikan zakat')}
      <div class="card"><dl class="kv">
        <div><dt>Haul genap</dt><dd>${hijriStr(zkDates(Z).due)}</dd></div>
        <div><dt>Harta bersih</dt><dd>${rp(zkNet(Z))}</dd></div></dl></div>
      <p class="hero-label" style="margin-top:var(--rf-space-4)">Zakat (2,5% dari harta bersih)</p>
      <div class="amount" aria-live="polite"><span class="amount__cur">Rp</span><span class="amount__num" id="u-num"></span></div>
      <p class="field-label">Akun sumber</p><div class="chips">${acctList}</div>
      <p class="field-label">Kategori</p><p style="margin:0">Zakat mal ${r ? '(ruang Memberi)' : ''}</p>
      ${r ? '' : `<div class="banner banner--info">${icon('clock')}<span>Ruang Memberi belum ada. Pakai pola Tiga hak dulu.</span></div>`}
      <div class="banner" id="u-banner" hidden></div>
      <p class="disclaimer" style="margin-top:var(--rf-space-2)">Jumlah bisa diubah. Bantuan hitung, bukan fatwa.</p>
      <div class="numpad">${numpadHtml('u-key')}</div>
      <div class="actions"><button type="button" class="btn btn--primary btn--block" id="u-save" data-action="u-save">Tunaikan zakat</button></div>`;
  }

  function updateTunai() {
    const el = $('#u-num');
    if (!el) return;
    const n = Number(U.amount || 0);
    el.textContent = n ? nf.format(n) : '0';
    el.classList.toggle('is-empty', !n);
    $('#u-save').disabled = !n || !S.rooms.memberi;
    const r = S.rooms.memberi;
    const banner = $('#u-banner');
    const over = r ? r.used + n - r.alloc : 0;
    banner.hidden = !(n && r && over > 0);
    if (!banner.hidden) banner.innerHTML = `${icon('alert')}<span>Jatah Memberi bulan ini terlampaui ${rp(over)}. Zakat tetap bisa ditunaikan.</span>`;
  }

  /* ------------------------------------ S09 Detail dan edit transaksi */

  /** Efek satu transaksi ke ruang, pos, dan saldo. sign +1 menerapkan, -1 membatalkan. */
  function txEffect(t, sign) {
    if (t.kind === 'expense') {
      const n = -t.amt;
      const r = S.rooms[t.room];
      if (r) {
        r.used += sign * n;
        const p = r.pos.find((x) => x.name === t.cat);
        if (p) p.used += sign * n;
      }
      S.acct[t.acct].bal -= sign * n;
    } else if (t.kind === 'income') {
      S.acct[t.acct].bal += sign * t.amt;
      Object.entries(t.alloc || {}).forEach(([id, a]) => {
        if (S.rooms[id]) S.rooms[id].alloc += sign * a;
      });
    } else if (t.kind === 'transfer') {
      S.acct[t.acct].bal -= sign * t.amt;
      S.acct[t.to].bal += sign * t.amt;
    }
  }

  // Deklarasi fungsi (bukan const) karena newDraft() memanggilnya saat pemuatan awal.
  function otherAcct(id) {
    return S.acctOrder.find((x) => x !== id) || null;
  }

  function txdetailScreen() {
    const t = S.tx.find((x) => x.id === route.id);
    if (!t) return `${topbarNav('Detail transaksi')}<p class="empty">Transaksi tidak ditemukan.</p>`;
    const kindLabel = { expense: 'Pengeluaran', income: 'Pemasukan', transfer: 'Transfer' }[t.kind];
    let fields = '';
    if (t.kind === 'expense') {
      const r = S.rooms[TD.room];
      const roomChips = S.order
        .map((id) => `<button type="button" class="chip-btn" aria-pressed="${TD.room === id}" data-action="td-room" data-arg="${id}"><span class="dot" style="--seg:${color(S.rooms[id])}"></span>${esc(S.rooms[id].name)}</button>`)
        .join('');
      const cats = (r ? catsOf(r) : [])
        .map((p) => `<button type="button" class="chip-btn" aria-pressed="${TD.cat === p.name}" data-action="td-cat" data-arg="${esc(p.name)}">${esc(p.name)}</button>`)
        .join('');
      fields = `<p class="field-label">Ruang</p><div class="chips">${roomChips}</div>
        <p class="field-label">Kategori</p><div class="chips">${cats}</div>
        <p class="field-label">Akun</p><div class="chips">${acctChips(TD.acct, 'td-acct')}</div>
        <div class="switch-row"><div><b>Jadikan favorit</b><p class="list-row__sub">Muncul di Catat kilat.</p></div>
          <button type="button" class="switch" role="switch" aria-checked="${TD.fav}" aria-label="Jadikan favorit" data-action="td-fav"></button></div>`;
    } else if (t.kind === 'income') {
      const src = ['Gaji', 'Usaha', 'Freelance', 'Lainnya']
        .map((s) => `<button type="button" class="chip-btn" aria-pressed="${TD.source === s}" data-action="td-src" data-arg="${s}">${s}</button>`)
        .join('');
      fields = `<p class="field-label">Sumber</p><div class="chips">${src}</div>
        <p class="field-label">Akun</p><div class="chips">${acctChips(TD.acct, 'td-acct')}</div>
        <h2 class="section-title">Dialirkan saat itu</h2><div class="card" id="td-alloc"></div>`;
    } else {
      const to = accts().filter((a) => a.id !== TD.acct)
        .map((a) => `<button type="button" class="chip-btn" aria-pressed="${TD.to === a.id}" data-action="td-to" data-arg="${a.id}">${esc(a.name)}</button>`)
        .join('');
      fields = `<p class="field-label">Dari</p><div class="chips">${acctChips(TD.acct, 'td-acct')}</div>
        <p class="field-label">Ke</p><div class="chips">${to}</div>`;
    }
    return `${topbarNav('Detail transaksi')}
      <p class="hero-label" style="text-align:center">${kindLabel} · ${esc(t.date)}</p>
      <div class="amount" aria-live="polite" style="margin-top:var(--rf-space-2)"><span class="amount__cur">Rp</span><span class="amount__num" id="td-num"></span></div>
      ${fields}
      <p class="field-label"><label for="td-note">Catatan</label></p>
      <input id="td-note" class="field-input" type="text" autocomplete="off" value="${esc(TD.note)}" />
      <div class="numpad">${numpadHtml('td-key')}</div>
      <div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="td-save">Simpan</button>
        <button type="button" class="btn btn--text btn--block" data-action="td-delete">Hapus transaksi</button></div>`;
  }

  function updateTxDetail() {
    const el = $('#td-num');
    if (!el) return;
    const n = Number(TD.amount || 0);
    el.textContent = n ? nf.format(n) : '0';
    el.classList.toggle('is-empty', !n);
    const t = S.tx.find((x) => x.id === TD.id);
    const box = $('#td-alloc');
    if (box && t && t.pcts) {
      const ids = Object.keys(t.pcts).filter((id) => S.rooms[id]);
      const { parts } = split(n, t.pcts, ids);
      box.innerHTML =
        ids.map((id) => `<div class="alloc-row">${roomIcon(S.rooms[id])}<div class="alloc-row__main"><p class="alloc-row__name">${esc(S.rooms[id].name)}</p><p class="alloc-row__pct">${t.pcts[id]}%</p></div><span class="alloc-row__amt">${rp(parts[id])}</span></div>`).join('') +
        `<p class="list-row__sub" style="margin:var(--rf-space-2) 0 0">Aturan yang berlaku saat itu. Ruang dan persentasenya tidak bisa diubah dari sini.</p>`;
    }
    const save = $('[data-action="td-save"]');
    if (save) save.disabled = !n || (TD.kind === 'transfer' && (!TD.to || TD.to === TD.acct));
  }

  /* ------------------------------- S13 Kelola akun, kategori, favorit */

  const KIND_LABEL = { tunai: 'Tunai', bank: 'Bank', ewallet: 'Dompet digital' };

  function kelolaScreen() {
    const tab = route.tab || 'akun';
    const tabs = ['akun', 'kategori', 'favorit']
      .map((t) => `<button type="button" role="tab" aria-selected="${tab === t}" data-action="kelola-tab" data-arg="${t}">${{ akun: 'Akun', kategori: 'Kategori', favorit: 'Favorit' }[t]}</button>`)
      .join('');
    let body = '';
    if (tab === 'akun') {
      body =
        `<ul class="list">${accts()
          .map((a) => `<li><button type="button" class="list-row" data-action="acct-edit" data-arg="${a.id}"><span class="list-row__main"><p class="list-row__title">${esc(a.name)}</p><p class="list-row__sub">${KIND_LABEL[a.kind] || 'Akun'}</p></span><span class="list-row__end">${rpShort(a.bal)}</span>${icon('chevron')}</button></li>`)
          .join('')}</ul>
        <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--tonal btn--block" data-action="acct-add">${icon('plus')}Tambah akun</button></div>`;
    } else if (tab === 'kategori') {
      body =
        rooms()
          .map(
            (r) => `<h2 class="section-title">${esc(r.name)}</h2><ul class="list">${r.pos
              .map((p) => `<li><div class="list-row"><span class="list-row__main"><p class="list-row__title">${esc(p.name)}</p><p class="list-row__sub">${p.name === 'Zakat mal' || p.name === 'Tak terlacak' ? 'Kategori sistem, tidak bisa dihapus' : p.base ? 'Ikut jatah ruang' : 'Tanpa jatah tersendiri'}</p></span></div></li>`)
              .join('')}</ul>
              <div class="actions" style="margin-top:var(--rf-space-2)"><button type="button" class="btn btn--text btn--block" data-action="cat-add" data-arg="${r.id}">${icon('plus')}Tambah kategori di ${esc(r.name)}</button></div>`
          )
          .join('') || '<p class="empty">Belum ada ruang, jadi belum ada kategori.</p>';
    } else {
      body = S.favs.length
        ? `<p class="list-row__sub">Tampil di Catat kilat (paling banyak 6).</p><ul class="list">${S.favs
            .map((f) => `<li><div class="list-row"><span class="list-row__main"><p class="list-row__title">${esc(f.name)} · ${rpShort(f.amt)}</p><p class="list-row__sub">${esc(S.rooms[f.room] ? S.rooms[f.room].name : '-')}, ${esc(f.cat)}, ${esc(S.acct[f.acct] ? S.acct[f.acct].name : '-')}</p></span>
              <button type="button" class="btn btn--text btn--small" data-action="fav-del" data-arg="${f.id}">Hapus</button></div></li>`)
            .join('')}</ul><p class="disclaimer">Favorit baru dibuat dari Catat (Jadikan favorit).</p>`
        : '<p class="empty">Belum ada favorit. Jadikan favorit dari layar Catat.</p>';
    }
    return `${topbarNav('Akun dan kategori')}
      <div class="seg-control" role="tablist" aria-label="Bagian">${tabs}</div><div style="margin-top:var(--rf-space-3)">${body}</div>`;
  }

  /* ------------------------------------ S19 Keamanan, PIN, dan kunci */

  function keamananScreen() {
    const s = S.sec;
    const sw = (label, sub, on, action, disabled) =>
      `<div class="switch-row"><div><b>${label}</b><p class="list-row__sub">${sub}</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${on}" aria-label="${label}" data-action="${action}" ${disabled ? 'disabled' : ''}></button></div>`;
    const lock = [['segera', 'Segera'], ['1', 'Setelah 1 menit'], ['5', 'Setelah 5 menit']]
      .map(([v, l]) => `<button type="button" class="chip-btn" aria-pressed="${s.lock === v}" data-action="sec-lock" data-arg="${v}">${l}</button>`)
      .join('');
    return `${topbarNav('Keamanan')}
      ${sw('Kunci aplikasi', 'Minta PIN saat membuka', s.pinOn, 'sec-pin')}
      ${s.pinOn ? `<ul class="list"><li><button type="button" class="list-row" data-action="pin-start" data-arg="ubah"><span class="list-row__main"><p class="list-row__title">Ubah PIN</p></span>${icon('chevron')}</button></li></ul>` : ''}
      ${sw('Buka dengan sidik jari', s.pinOn ? 'PIN tetap jadi cadangan' : 'Buat PIN dulu', s.bio, 'sec-bio', !s.pinOn)}
      <p class="field-label">Kunci otomatis</p><div class="chips">${lock}</div>
      ${sw('Sembunyikan di aplikasi terbaru', 'Layar tidak tampil di pratinjau dan tangkapan layar', s.hide, 'sec-hide')}
      ${s.pinOn ? `<div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--tonal btn--block" data-action="kunci-sim">Simulasikan kunci aplikasi</button></div>` : ''}
      <p class="disclaimer">Lupa PIN tidak bisa diatur ulang karena tidak ada akun. Data dipulihkan dari cadangan (Backup dan restore). Keamanan selalu gratis.</p>`;
  }

  function pinPad(action) {
    const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'back']
      .map((k) =>
        k === ''
          ? '<span></span>'
          : k === 'back'
            ? `<button type="button" class="key" data-action="${action}" data-arg="back" aria-label="Hapus angka terakhir">${icon('backspace')}</button>`
            : `<button type="button" class="key" data-action="${action}" data-arg="${k}">${k}</button>`
      )
      .join('');
    return `<div class="numpad">${keys}</div>`;
  }

  const pinDots = (n) => `<div class="pin-dots" role="img" aria-label="${n} dari 6 angka terisi">${[0, 1, 2, 3, 4, 5].map((i) => `<span class="pin-dot${i < n ? ' is-on' : ''}"></span>`).join('')}</div>`;

  function pinScreen() {
    const title = PIN.step === 1 ? (PIN.mode === 'ubah' ? 'PIN baru' : 'Buat PIN') : 'Ulangi PIN';
    return `<header class="topbar topbar--nav"><button type="button" class="icon-btn" data-action="pin-cancel" aria-label="Batal">${icon('close')}</button><h1 tabindex="-1">${title}</h1></header>
      <p class="list-row__sub" style="text-align:center">${PIN.step === 1 ? 'Masukkan 6 angka' : 'Masukkan PIN yang sama sekali lagi'}</p>
      ${pinDots(PIN.cur.length)}
      <p class="pin-error" role="status">${esc(PIN.error)}</p>${pinPad('pin-key')}`;
  }

  function kunciScreen() {
    const locked = LOCK.tries >= 5;
    return `<div class="welcome" style="justify-content:flex-start;padding-top:var(--rf-space-6)">
      <div class="welcome__mark" aria-hidden="true"><span class="room-icon" style="--seg:var(--rf-primary)">${icon('lock')}</span></div>
      <h1 class="welcome__brand" style="font-size:var(--rf-size-headline)" tabindex="-1">Rizqflow terkunci</h1>
      <p class="list-row__sub">${locked ? 'Terlalu banyak salah. Coba lagi dalam 30 detik. Datamu tidak dihapus.' : 'Masukkan PIN'}</p>
      ${pinDots(LOCK.cur.length)}<p class="pin-error" role="status">${esc(LOCK.error)}</p>
      ${locked ? '' : pinPad('lock-key')}
      ${S.sec.bio && !locked ? `<button type="button" class="btn btn--tonal btn--block" data-action="lock-bio">Gunakan sidik jari (simulasi)</button>` : ''}
      <button type="button" class="btn btn--text btn--block" data-action="back">Tutup simulasi</button></div>`;
  }

  /* ------------------------ S20 Backup, restore, ekspor, dan impor */

  function cadanganScreen() {
    return `${topbarNav('Backup dan data')}
      <div class="card"><p class="hero-label">Cadangan terakhir</p>
        <p style="margin:2px 0 0;font-weight:700">${backupStore ? esc(backupStore.when) + ', di ponsel ini' : 'Belum ada cadangan'}</p></div>
      <h2 class="section-title">Cadangan terenkripsi</h2>
      <div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="bk-make">Cadangkan sekarang</button>
        <button type="button" class="btn btn--tonal btn--block" data-action="bk-restore">Pulihkan dari berkas</button></div>
      <h2 class="section-title">Ekspor dan impor</h2>
      <ul class="list">
        <li><button type="button" class="list-row" data-action="csv-export"><span class="list-row__main"><p class="list-row__title">Ekspor transaksi (CSV)</p><p class="list-row__sub">Selalu gratis</p></span>${icon('chevron')}</button></li>
        <li><button type="button" class="list-row" data-action="open-impor"><span class="list-row__main"><p class="list-row__title">Impor dari Transaksi Harian</p><p class="list-row__sub">CSV hasil ekspor Notion</p></span>${icon('chevron')}</button></li></ul>
      <p class="disclaimer">Data hanya ada di ponsel ini. Cadangkan secara berkala. Cadangan dienkripsi dengan sandi (AES-256-GCM); sandi tidak bisa dipulihkan.</p>`;
  }

  function imporScreen() {
    if (!IM.picked) {
      return `${topbarNav('Impor dari Transaksi Harian')}
        <p class="list-row__sub">Pilih berkas CSV hasil ekspor database Transaksi Harian dari Notion. Rizqflow menampilkan pratinjau dulu; belum ada yang tersimpan.</p>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--primary btn--block" data-action="impor-pick">Pilih berkas CSV (simulasi)</button></div>`;
    }
    return `${topbarNav('Pratinjau impor')}
      <p class="list-row__sub" style="margin:0 0 var(--rf-space-3)">Contoh dari pola berkas Transaksi Harian: 239 baris, 27 Juni sampai 4 September 2026.</p>
      <div class="card"><dl class="kv">
        <div><dt>Pengeluaran</dt><dd>182 baris</dd></div>
        <div><dt>Pemasukan</dt><dd>3 baris</dd></div>
        <div><dt>Transfer antar-akun</dt><dd>27 (dari 54 baris "(Out)" dan "(In)")</dd></div></dl></div>
      <h2 class="section-title">Yang dirapikan</h2>
      <ul class="list">
        <li><div class="list-row">${icon('check')}<span class="list-row__main"><p class="list-row__title">Pasangan transfer disatukan</p><p class="list-row__sub">Sisi masuk tidak dianggap rezeki, jadi tidak dialirkan ke ruang.</p></span></div></li>
        <li><div class="list-row">${icon('check')}<span class="list-row__main"><p class="list-row__title">Tanda nominal disamakan</p><p class="list-row__sub">195 pengeluaran bertanda negatif dan 14 positif dibaca sebagai pengeluaran.</p></span></div></li>
        <li><div class="list-row">${icon('check')}<span class="list-row__main"><p class="list-row__title">Kategori berhierarki dipecah</p><p class="list-row__sub">42 deskripsi seperti "Transportation &gt; BBM".</p></span></div></li></ul>
      <h2 class="section-title">Pemetaan</h2>
      <p class="list-row__sub">Akun di berkas dipetakan ke akun Rizqflow; yang belum ada dibuat baru. Kategori dan ruang dipetakan per kategori.</p>
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="impor-go">Impor 212 transaksi (simulasi)</button>
        <button type="button" class="btn btn--text btn--block" data-action="back">Batal</button></div>`;
  }

  /* ------------------------ S22 Tampilan, S23 Tentang dan disclaimer */

  function tampilanScreen() {
    const cur = document.documentElement.dataset.theme || 'auto';
    const chip = (grp, v, l, on, action) => `<button type="button" class="chip-btn" aria-pressed="${on}" data-action="${action}" data-arg="${v}">${l}</button>`;
    return `${topbarNav('Tampilan dan bahasa')}
      <p class="field-label">Tema</p><div class="chips">${[['auto', 'Otomatis'], ['light', 'Terang'], ['dark', 'Gelap']].map(([v, l]) => chip('t', v, l, cur === v, 'theme-set')).join('')}</div>
      <p class="field-label">Bahasa</p><div class="chips">${[['id', 'Indonesia'], ['en', 'English']].map(([v, l]) => chip('l', v, l, LANG === v, 'lang-set')).join('')}</div>
      <p class="list-row__sub" style="margin-top:var(--rf-space-4)">Ukuran teks mengikuti pengaturan ponsel. Angka utama dibatasi 1,25 kali supaya tidak pecah. Tema tambahan berbayar ada di Pro.</p>
      <div class="switch-row"><div><b>Mode demo</b><p class="list-row__sub">Coba dengan data contoh, terpisah dari datamu.</p></div>
        <button type="button" class="switch" role="switch" aria-checked="${S.demo}" aria-label="Mode demo" data-action="demo-toggle"></button></div>`;
  }

  function tentangScreen() {
    return `${topbarNav('Tentang')}
      <p style="margin:0;font-family:var(--rf-font-display);font-size:var(--rf-size-headline)">Rizqflow</p>
      <p class="list-row__sub" style="margin:0">Versi 0.1.0</p>
      <h2 class="section-title">Privasi</h2>
      <p style="margin:0">Data tersimpan di ponsel ini. Tidak ada akun, iklan, atau pelacak.</p>
      <h2 class="section-title">Asumsi fikih zakat mal</h2>
      <div class="card"><dl class="kv">
        <div><dt>Nisab</dt><dd>85 gram emas</dd></div>
        <div><dt>Tarif</dt><dd>2,5%</dd></div>
        <div><dt>Haul</dt><dd>1 tahun Hijriyah (Umm al-Qura)</dd></div>
        <div><dt>Sumber</dt><dd><span class="muted">Asumsi awal, belum diverifikasi dengan kitab</span></dd></div></dl></div>
      <p class="disclaimer">Bantuan hitung, bukan fatwa. Konsultasikan dengan ulama atau lembaga zakat.</p>
      <ul class="list" style="margin-top:var(--rf-space-4)">
        ${['Kebijakan privasi', 'Lisensi pihak ketiga', 'Kirim masukan'].map((t) => `<li><button type="button" class="list-row" data-action="info" data-arg="${t}: tautan disimulasikan di prototipe."><span class="list-row__main"><p class="list-row__title">${t}</p></span>${icon('chevron')}</button></li>`).join('')}</ul>`;
  }

  /* -------------------------------- S10 tambah dan arsip ruang (sheet) */

  const ROOM_KINDS = {
    menunaikan: { label: 'Menunaikan', verb: 'Tersalur', hint: 'Terpenuhi saat jatah tersalurkan penuh, misalnya sedekah.' },
    menumbuhkan: { label: 'Menumbuhkan', verb: 'Terinvestasi', hint: 'Terpenuhi saat jatah ditabung atau diinvestasikan penuh.' },
    mencukupi: { label: 'Mencukupi', verb: 'Terpakai', hint: 'Perlu perhatian saat pemakaian mencapai 85% jatah.' },
  };
  const ROOM_ICONS = ['heart', 'sprout', 'home', 'star', 'bolt'];

  function roomAddSheet() {
    const kinds = Object.entries(ROOM_KINDS)
      .map(([k, v]) => `<button type="button" class="chip-btn" aria-pressed="${SH.kind === k}" data-action="sh-pick" data-arg="kind:${k}">${v.label}</button>`)
      .join('');
    const icons = ROOM_ICONS.map((i) => `<button type="button" class="chip-btn" aria-pressed="${SH.icon === i}" data-action="sh-pick" data-arg="icon:${i}" aria-label="Ikon ${i}">${icon(i)}</button>`).join('');
    showSheet(
      `<div class="sheet__grab"></div><h2>Ruang baru</h2>
      <p class="field-label"><label for="sh-name">Nama</label></p><input id="sh-name" class="field-input" type="text" maxlength="24" autocomplete="off" placeholder="Contoh: Orang tua" />
      <p class="field-label">Tipe</p><div class="chips">${kinds}</div>
      <p class="list-row__sub" id="sh-hint" style="margin:6px 0 0">${ROOM_KINDS[SH.kind].hint}</p>
      <p class="field-label">Ikon</p><div class="chips">${icons}</div>
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="room-add-save">Tambah ruang</button>
        <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
      'Ruang baru'
    );
  }

  function acctSheet(a) {
    const kinds = Object.entries(KIND_LABEL)
      .map(([k, l]) => `<button type="button" class="chip-btn" aria-pressed="${SH.kind === k}" data-action="sh-pick" data-arg="kind:${k}">${l}</button>`)
      .join('');
    showSheet(
      `<div class="sheet__grab"></div><h2>${a ? 'Ubah akun' : 'Akun baru'}</h2>
      <p class="field-label"><label for="sh-name">Nama akun</label></p><input id="sh-name" class="field-input" type="text" maxlength="30" autocomplete="off" value="${a ? esc(a.name) : ''}" />
      ${a ? '' : `<p class="field-label">Jenis</p><div class="chips">${kinds}</div><p class="field-label"><label for="sh-bal">Saldo awal</label></p><input id="sh-bal" class="field-input" type="text" inputmode="numeric" value="0" />`}
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="acct-save">${a ? 'Simpan' : 'Tambah akun'}</button>
        ${a ? `<button type="button" class="btn btn--tonal btn--block" data-action="acct-recon" data-arg="${a.id}">Cocokkan saldo</button><button type="button" class="btn btn--text btn--block" data-action="acct-archive" data-arg="${a.id}">Arsipkan akun</button>` : ''}
        <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
      a ? 'Ubah akun' : 'Akun baru'
    );
  }

  /* ---------------------- S08 Daftar transaksi, S06 Transfer, S18 Lainnya */

  const TX_KINDS = [['all', 'Semua'], ['income', 'Masuk'], ['expense', 'Keluar'], ['transfer', 'Transfer']];

  function txMatches(t) {
    if (route.acct && t.acct !== route.acct && t.to !== route.acct) return false;
    const k = route.kind || 'all';
    if (k !== 'all' && t.kind !== k) return false;
    const q = (route.q || '').trim().toLowerCase();
    if (!q) return true;
    const digits = q.replace(/\D/g, '');
    return `${t.title} ${t.sub} ${t.note || ''}`.toLowerCase().includes(q) || (digits !== '' && String(Math.abs(t.amt)).includes(digits));
  }

  function txListHtml() {
    const groups = [];
    S.tx.filter(txMatches).forEach((t) => {
      let g = groups.find((x) => x.date === t.date);
      if (!g) groups.push((g = { date: t.date, items: [] }));
      g.items.push(t);
    });
    if (!groups.length) {
      const msg = route.q
        ? 'Tidak ada yang cocok dengan pencarianmu.'
        : route.acct
          ? 'Belum ada transaksi di akun ini.'
          : 'Belum ada transaksi. Rezeki dan pengeluaran yang kamu catat akan muncul di sini.';
      return `<p class="empty">${msg}</p>`;
    }
    return groups
      .map((g) => {
        const rows = g.items
          .map((t) => {
            const r = t.room ? S.rooms[t.room] : null;
            const tile =
              t.kind === 'transfer'
                ? `<span class="room-icon" style="--seg:var(--rf-primary)">${icon('swap')}</span>`
                : r ? roomIcon(r) : `<span class="room-icon" style="--seg:var(--rf-primary)">${icon('income')}</span>`;
            const amt = t.kind === 'transfer' ? rp(t.amt) : `${t.amt > 0 ? '+' : ''}${rp(t.amt)}`;
            return `<li><button type="button" class="list-row" data-action="open-tx" data-arg="${t.id}">${tile}<span class="list-row__main"><p class="list-row__title">${esc(t.title)}</p><p class="list-row__sub">${esc(t.sub)}</p></span>
              <span class="list-row__end">${amt}</span></button></li>`;
          })
          .join('');
        return `<h2 class="section-title">${esc(g.date)}</h2><ul class="list">${rows}</ul>`;
      })
      .join('');
  }

  function transaksiScreen() {
    const filter = route.acct
      ? `<div class="chips" style="margin:var(--rf-space-2) 0"><button type="button" class="chip-btn" aria-pressed="true" data-action="clear-filter">Akun: ${esc(S.acct[route.acct].name)} ✕</button></div>`
      : '';
    const kinds = TX_KINDS.map(([k, l]) => `<button type="button" class="chip-btn" aria-pressed="${(route.kind || 'all') === k}" data-action="tx-kind" data-arg="${k}">${l}</button>`).join('');
    // Petunjuk hari kosong: lembut, tanpa streak dan tanpa warna merah.
    const yesterdayEmpty = S.tx.length > 0 && !S.checked['19 Sep'] && !S.tx.some((t) => t.date === '19 Sep' && t.amt < 0);
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
    return `<header class="topbar"><div><h1 tabindex="-1">Transaksi</h1><p class="topbar__sub">September 2026</p></div></header>
      <input id="tx-q" class="field-input" type="search" placeholder="Cari transaksi" aria-label="Cari catatan atau nominal" value="${esc(route.q || '')}" />
      <div class="chips" style="margin-top:var(--rf-space-2)">${kinds}</div>${filter}${hint}${drafRow}
      <div id="tx-list">${txListHtml()}</div>`;
  }

  function transferFields(d) {
    if (S.acctOrder.length < 2) {
      return `<div class="banner banner--info">${icon('clock')}<span>Transfer butuh dua akun. Tambah akun lain di Lainnya, Akun, kategori, dan favorit.</span></div>`;
    }
    const from = acctChips(d.from, 'tr-from');
    const to = accts()
      .filter((a) => a.id !== d.from)
      .map((a) => `<button type="button" class="chip-btn" aria-pressed="${d.to === a.id}" data-action="tr-to" data-arg="${a.id}">${esc(a.name)}</button>`)
      .join('');
    return `<p class="field-label">Dari</p><div class="chips">${from}</div><p class="field-label">Ke</p><div class="chips">${to}</div>
      <p class="list-row__sub" style="margin:var(--rf-space-3) 0 0">Memindahkan uang antar akun. Bukan pemasukan atau pengeluaran, jadi tidak masuk ruang mana pun.</p>`;
  }

  function lainnyaScreen() {
    const act = (title, sub, action, arg, iconName) =>
      `<li><button type="button" class="list-row" data-action="${action}" data-arg="${arg || ''}"><span class="attn-icon" style="color:var(--rf-primary)">${icon(iconName)}</span>
        <span class="list-row__main"><p class="list-row__title">${title}</p><p class="list-row__sub">${sub}</p></span>${icon('chevron')}</button></li>`;
    const stale = accts().slice().sort((a, b) => b.days - a.days)[0];
    const zk = S.zakat ? 'Nisab dan haul, tunaikan zakat' : 'Belum aktif · mulai dengan mengisi harta';
    return `<header class="topbar"><div><h1 tabindex="-1">Lainnya</h1></div></header>
      <ul class="list"><li><button type="button" class="list-row" data-action="pro"><span class="attn-icon" style="color:var(--rf-primary)">${icon('star')}</span>
        <span class="list-row__main"><p class="list-row__title">Rizqflow Pro${S.pro ? ' (aktif)' : ''}</p><p class="list-row__sub">Sekali bayar, tanpa langganan</p></span>${icon('chevron')}</button></li></ul>
      <h2 class="section-title">Catat dan pantau</h2>
      <ul class="list">
        ${act('Koreksi saldo', 'Cocokkan saldo akun dengan catatan', 'open-koreksi', stale ? stale.id : '', 'balance')}
        ${act('Pengingat harian', S.reminder.on ? `Aktif, pukul ${S.reminder.hour}` : 'Dimatikan', 'open-pengingat', '', 'bell')}
        ${act('Tangkap otomatis', 'Pro, versi 1.1', 'open-tangkap', '', 'inbox')}
        ${act('Widget catat kilat', 'Pro · pintasan ikon, tile, dan balasan notifikasi tetap gratis', 'open-widget', '', 'bolt')}
      </ul>
      <h2 class="section-title">Atur</h2>
      <ul class="list">
        ${act('Akun, kategori, dan favorit', `${accts().length} akun`, 'open-kelola', '', 'home')}
        ${act('Aturan alokasi', 'Ubah pembagian rezeki ke ruang', 'open-aturan', '', 'sliders')}
        ${act('Zakat', zk, 'open-zakat', '', 'heart')}
      </ul>
      <h2 class="section-title">Data dan keamanan</h2>
      <ul class="list">
        ${act('Keamanan', `PIN dan sidik jari, selalu gratis${S.sec.pinOn ? ' · aktif' : ''}`, 'open-keamanan', '', 'lock')}
        ${act('Backup, restore, dan ekspor', 'Selalu gratis', 'open-cadangan', '', 'inbox')}
      </ul>
      <h2 class="section-title">Aplikasi</h2>
      <ul class="list">
        ${act('Tampilan dan bahasa', 'Tema, bahasa, mode demo', 'open-tampilan', '', 'sliders')}
        ${act('Tentang dan disclaimer', 'Versi, privasi, asumsi fikih', 'open-tentang', '', 'check')}
      </ul>`;
  }

  /** Sheet konfirmasi bersama: judul, keterangan, satu aksi utama, dan Batal. */
  function confirmSheet(title, text, yesLabel, yesAction, arg) {
    showSheet(
      `<div class="sheet__grab"></div><h2>${esc(title)}</h2>
      <p class="list-row__sub" style="margin:0 0 var(--rf-space-4)">${esc(text)}</p>
      <div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="${yesAction}" data-arg="${arg || ''}">${esc(yesLabel)}</button>
        <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
      title
    );
  }

  const redrawTd = () => {
    screenEl.innerHTML = txdetailScreen();
    updateTxDetail();
  };
  const syncTd = () => {
    const n = $('#td-note');
    if (n) TD.note = n.value;
  };
  /* ------------------------------------ S01-S04: onboarding (flow F1) */

  const newOB = () => ({
    template: 'tiga', pcts: { memberi: 10, diri: 30, keluarga: 60 },
    acctType: 'tunai', acctName: 'Tunai', nameTouched: false, balance: '',
  });

  const ACCT_TYPES = [
    { id: 'tunai', label: 'Tunai', def: 'Tunai', hint: 'Contoh: Dompet' },
    { id: 'bank', label: 'Bank', def: '', hint: 'Contoh: Bank Jago' },
    { id: 'ewallet', label: 'Dompet digital', def: '', hint: 'Contoh: GoPay' },
  ];

  const stepNote = (n) => `<p class="step-note">Langkah ${n} dari ${OB.template === 'tiga' ? 3 : 2}</p>`;

  /** S01 Sambutan: tagline dan satu tombol Mulai, tanpa slide berlapis. */
  function sambutanScreen() {
    const marks = TEMPLATE_IDS.map((id) => `<span class="room-icon" style="--seg:${color(TEMPLATE[id])}">${icon(TEMPLATE[id].icon)}</span>`).join('');
    const point = (t) => `<li>${icon('check')}<span>${t}</span></li>`;
    return `<div class="welcome">
      <div class="welcome__mark" aria-hidden="true">${marks}</div>
      <h1 class="welcome__brand" tabindex="-1">Rizqflow</h1>
      <p class="welcome__tag">Rezeki mengalir, setiap hak terpenuhi.</p>
      <ul class="welcome__points">${point('Data tersimpan di ponselmu, tanpa akun')}${point('Jalan tanpa internet')}${point('Tanpa iklan')}</ul>
      <div class="actions welcome__actions">
        <button type="button" class="btn btn--primary btn--block" data-action="ob-start">Mulai</button>
        <button type="button" class="btn btn--text btn--block" data-action="info" data-arg="Pulihkan dari cadangan (S20, flow F7) belum dibuat di prototipe.">Pulihkan dari cadangan</button>
      </div></div>`;
  }

  /** S02 Pilih pola ruang: Tiga hak atau Mulai kosong. Bisa diubah nanti. */
  function polaScreen() {
    const three = TEMPLATE_IDS.map((id) => {
      const r = TEMPLATE[id];
      return `<li>${roomIcon(r)}<span><b>${esc(r.name)}</b> <span class="muted">· ${esc(TEMPLATE_DESC[id])}</span></span></li>`;
    }).join('');
    const card = (id, title, sub, extra) =>
      `<button type="button" class="choice" aria-pressed="${OB.template === id}" data-action="ob-template" data-arg="${id}">
        <span class="choice__radio" aria-hidden="true"></span>
        <span class="choice__body"><b>${title}</b><span class="list-row__sub">${sub}</span>${extra || ''}</span></button>`;
    return `${topbarNav('Pilih pola ruang')}${stepNote(1)}
      <p class="list-row__sub" style="margin:var(--rf-space-2) 0 var(--rf-space-4)">Ruang adalah tempat rezeki dialirkan. Pilihan ini bisa diubah kapan saja.</p>
      ${card('tiga', 'Tiga hak', 'Cara paling mudah untuk mulai. Rezeki dibagi ke tiga ruang:', `<ul class="choice__list">${three}</ul>`)}
      ${card('kosong', 'Mulai kosong', 'Susun ruangmu sendiri nanti di tab Ruang. Rezeki yang masuk menunggu sampai ada ruang.')}
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="ob-next-pola">Lanjut</button></div>`;
  }

  /** S03 Atur persentase: total selalu 100% karena Keluarga menerima sisanya. */
  function persenScreen() {
    return `${topbarNav('Atur pembagian')}${stepNote(2)}
      <p class="list-row__sub" style="margin:var(--rf-space-2) 0 0">Setiap rezeki yang masuk dialirkan ke ruang-ruang ini. Bisa diubah kapan saja di Ruang, Aturan alokasi.</p>
      ${ruleEditor()}
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="ob-next-persen">Lanjut</button></div>`;
  }

  /** S04 Tambah akun pertama: jenis, nama, dan saldo awal (saldo awal bukan rezeki). */
  function akunScreen() {
    const types = ACCT_TYPES.map(
      (t) => `<button type="button" class="chip-btn" aria-pressed="${OB.acctType === t.id}" data-action="ob-type" data-arg="${t.id}">${t.label}</button>`
    ).join('');
    const t = ACCT_TYPES.find((x) => x.id === OB.acctType);
    return `${topbarNav('Akun pertama')}${stepNote(OB.template === 'tiga' ? 3 : 2)}
      <p class="list-row__sub" style="margin:var(--rf-space-2) 0 0">Tempat uangmu berada: dompet, rekening, atau dompet digital. Akun lain bisa ditambah nanti.</p>
      <p class="field-label">Jenis akun</p><div class="chips">${types}</div>
      <p class="field-label"><label for="ob-name">Nama akun</label></p>
      <input id="ob-name" class="field-input" type="text" maxlength="30" autocomplete="off" placeholder="${esc(t.hint)}" value="${esc(OB.acctName)}" />
      <p class="field-label" style="margin-top:var(--rf-space-4)">Saldo awal</p>
      <div class="amount" style="margin:var(--rf-space-3) 0" aria-live="polite"><span class="amount__cur">Rp</span><span class="amount__num" id="ob-num"></span></div>
      <p class="list-row__sub" style="text-align:center;margin:0">Saldo yang ada sekarang. Ini bukan rezeki, jadi tidak dialirkan ke ruang.</p>
      <div class="numpad">${numpadHtml('ob-key')}</div>
      <div class="actions"><button type="button" class="btn btn--primary btn--block" id="ob-done" data-action="ob-done">Mulai memakai</button></div>`;
  }

  function updateOb() {
    const el = $('#ob-num');
    if (!el) return;
    const n = Number(OB.balance || 0);
    el.textContent = n ? nf.format(n) : '0';
    el.classList.toggle('is-empty', !n);
    const done = $('#ob-done');
    if (done) done.disabled = !OB.acctName.trim();
  }

  function startOnboarding() {
    OB = newOB();
    R = null;
    realState = null;
    hideNotif();
    closeSheet();
    dismissToast();
    S = seed(); // belum dipakai di layar onboarding
    draft = newDraft();
    A = K = K2 = undo = prevView = null;
    stack = [];
    route = { name: 'sambutan' };
    render();
  }

  /* --------------------- penyunting persentase: S03 dan S12 (flow F4) */

  /**
   * Dipakai bersama. Di S03 (kind 'ob') ruang terakhir menerima sisanya sehingga total selalu 100%.
   * Di S12 (kind 'ar') semua bebas diubah, tetapi Simpan hanya aktif bila total tepat 100%.
   */
  function ruleEditor() {
    const auto = R.kind === 'ob';
    const rows = R.ids
      .map((id, i) => {
        const r = R.rooms[id];
        const isRest = auto && i === R.ids.length - 1;
        const ctl = isRest
          ? `<p class="list-row__sub" style="margin:var(--rf-space-1) 0 0">Otomatis: menerima sisanya supaya total tepat 100%.</p>`
          : `<div class="rule-row__ctl">
              <button type="button" class="step-btn" data-action="rule-step" data-arg="${id}:-1" aria-label="Kurangi ${esc(r.name)} 1 persen">−</button>
              <input type="range" min="0" max="100" step="1" value="${R.pcts[id]}" data-rule="${id}" aria-label="Persentase ${esc(r.name)}" />
              <button type="button" class="step-btn" data-action="rule-step" data-arg="${id}:1" aria-label="Tambah ${esc(r.name)} 1 persen">+</button></div>`;
        return `<div class="rule-row${R.focus === id ? ' is-focus' : ''}" style="--seg:${color(r)}">
          <div class="rule-row__top">${roomIcon(r)}<div class="alloc-row__main"><p class="alloc-row__name">${esc(r.name)}</p>
          <p class="alloc-row__pct" id="rule-amt-${id}"></p></div><span class="rule-row__pct" id="rule-pct-${id}"></span></div>${ctl}</div>`;
      })
      .join('');
    return `<div class="card" style="margin-top:var(--rf-space-3)"><p class="hero-label">Contoh untuk ${rp(RULE_SAMPLE)}</p><div id="rule-summary"></div></div>
      <div class="card" style="margin-top:var(--rf-space-3)">${rows}
        <div class="total-line"><span>Total</span><span id="rule-total"></span></div></div>
      <div id="rule-note" role="status"></div>`;
  }

  const ruleSum = () => R.ids.reduce((a, id) => a + R.pcts[id], 0);
  const ruleDirty = () => !!R && R.kind === 'ar' && R.ids.some((id) => R.pcts[id] !== R.orig[id]);

  function updateRules() {
    if (!R || !R.ids.length) return;
    const sum = ruleSum();
    const { parts, rest } = split(RULE_SAMPLE, R.pcts, R.ids);
    R.ids.forEach((id) => {
      const p = $('#rule-pct-' + id);
      if (p) p.textContent = `${R.pcts[id]}%`;
      const a = $('#rule-amt-' + id);
      if (a) a.textContent = rp(parts[id]);
      const input = $(`[data-rule="${id}"]`);
      if (input && Number(input.value) !== R.pcts[id]) input.value = R.pcts[id];
    });
    const items = R.ids.map((id) => ({ id, name: R.rooms[id].name, color: color(R.rooms[id]), to: parts[id], from: parts[id] }));
    const aria = 'Contoh pembagian: ' + items.map((i) => `${i.name} ${rp(i.to)}`).join(', ') + (rest ? `, belum dialirkan ${rp(rest)}` : '');
    const summary = $('#rule-summary');
    if (summary) summary.innerHTML = stackBar(items, rest, aria) + legend(items, RULE_SAMPLE, rest);
    const total = $('#rule-total');
    if (total) {
      total.innerHTML =
        sum === 100
          ? `<span class="chip chip--good">${icon('check')}100%</span>`
          : `<span class="chip chip--attn">${icon('alert')}${sum}%</span>`;
    }
    const note = $('#rule-note');
    if (note) {
      note.innerHTML =
        sum === 100
          ? ''
          : `<div class="banner">${icon('alert')}<span>${sum < 100 ? `Total ${sum}%. Tambah ${100 - sum}% supaya genap 100%.` : `Total ${sum}%. Kurangi ${sum - 100}% supaya genap 100%.`} Sisanya tidak dialirkan ke ruang mana pun.</span></div>`;
    }
    const save = $('#ar-save');
    if (save) save.disabled = !(sum === 100 && ruleDirty());
  }

  function setRule(id, value) {
    let v = Math.max(0, Math.min(100, Math.round(value)));
    if (R.kind === 'ob') {
      const free = R.ids.slice(0, -1);
      const others = free.filter((x) => x !== id).reduce((a, x) => a + R.pcts[x], 0);
      v = Math.min(v, 100 - others);
      R.pcts[id] = v;
      R.pcts[R.ids[R.ids.length - 1]] = 100 - free.reduce((a, x) => a + R.pcts[x], 0);
    } else {
      R.pcts[id] = v;
    }
    updateRules();
  }

  /** S12 Aturan alokasi. Berlaku untuk pemasukan berikutnya; riwayat tidak berubah. */
  function aturanScreen() {
    if (!S.order.length) {
      return `${topbarNav('Aturan alokasi')}
        <p class="empty">Belum ada ruang, jadi belum ada yang diatur.</p>
        <button type="button" class="btn btn--tonal btn--block" data-action="apply-template">Pakai pola Tiga hak</button>`;
    }
    return `${topbarNav('Aturan alokasi')}
      <p class="list-row__sub" style="margin:0">Berlaku untuk pemasukan berikutnya. Riwayat yang sudah tercatat tidak berubah.</p>
      ${ruleEditor()}
      <ul class="list" style="margin-top:var(--rf-space-4)"><li><button type="button" class="list-row" data-action="ar-pro"><span class="attn-icon" style="color:var(--rf-primary)">${icon('lock')}</span>
        <span class="list-row__main"><p class="list-row__title">Aturan lanjutan</p><p class="list-row__sub">Pro · prioritas, batas atas, sisa mengalir</p></span>${icon('chevron')}</button></li></ul>
      <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" id="ar-save" data-action="ar-save" disabled>Simpan aturan</button></div>`;
  }

  function askDiscard() {
    showSheet(
      `<div class="sheet__grab"></div>
      <h2>Buang perubahan?</h2>
      <p class="list-row__sub" style="margin:0 0 var(--rf-space-4)">Perubahan pada aturan alokasi belum disimpan.</p>
      <div class="actions">
        <button type="button" class="btn btn--primary btn--block" data-action="close-sheet">Lanjut mengubah</button>
        <button type="button" class="btn btn--text btn--block" data-action="ar-discard">Buang perubahan</button>
      </div>`,
      'Buang perubahan?'
    );
  }

  const SCREENS = {
    denah: denahScreen, catat: catatScreen, alokasi: alokasiScreen, detail: detailScreen,
    haul: haulScreen, transaksi: transaksiScreen, ruang: ruangScreen, lainnya: lainnyaScreen,
    koreksi: koreksiScreen, pengingat: pengingatScreen, draf: drafScreen, tangkap: tangkapScreen,
    sambutan: sambutanScreen, pola: polaScreen, persen: persenScreen, akun: akunScreen, aturan: aturanScreen,
    txdetail: txdetailScreen, kelola: kelolaScreen, zakat: zakatScreen, harta: hartaScreen, tunai: tunaiScreen,
    keamanan: keamananScreen, pin: pinScreen, kunci: kunciScreen, cadangan: cadanganScreen, impor: imporScreen,
    tampilan: tampilanScreen, tentang: tentangScreen,
  };
  const NO_NAV = [
    'catat', 'alokasi', 'haul', 'koreksi', 'sambutan', 'pola', 'persen', 'akun', 'aturan',
    'txdetail', 'kelola', 'zakat', 'harta', 'tunai', 'keamanan', 'pin', 'kunci', 'cadangan', 'impor', 'tampilan', 'tentang',
  ];
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
    if (route.name === 'akun') updateOb();
    if (route.name === 'harta') updateHarta();
    if (route.name === 'tunai') updateTunai();
    if (route.name === 'txdetail') updateTxDetail();
    if (R && (route.name === 'persen' || route.name === 'aturan')) updateRules();
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

  function startAlokasi(label, amount, pendingId, acct, counted) {
    A = {
      label, amount, pendingId,
      counted: !!counted, // sisa dari alokasi sebelumnya: saldo dan transaksinya sudah tercatat
      acct: acct || defAcct(),
      pcts: Object.fromEntries(S.order.map((id) => [id, S.rooms[id].pct])),
      edit: false,
    };
    go('alokasi');
  }

  function confirmAlokasi() {
    const { parts, rest, valid } = split(A.amount, A.pcts);
    if (!valid) return;
    prevView = snapshot();
    const ids = S.order.filter((id) => parts[id] > 0);
    ids.forEach((id) => (S.rooms[id].alloc += parts[id]));
    if (A.pendingId) S.pending = S.pending.filter((p) => p.id !== A.pendingId);
    // Yang belum terbagi tidak hilang: tampil di Denah sebagai "belum dialirkan".
    if (rest > 0) {
      S.pending.push({ id: 'p' + (S.tx.length + 1) + '-' + rest, label: ids.length ? A.label + ' (sisa)' : A.label, amount: rest, counted: true });
    }
    if (!A.counted) {
      S.acct[A.acct].bal += A.amount;
      S.tx.unshift({
        id: newTxId(), kind: 'income', source: A.label, note: A.label, pcts: Object.assign({}, A.pcts), alloc: parts,
        date: 'Hari ini', title: A.label, amt: A.amount, room: null, acct: A.acct,
        sub: ids.length ? `Pemasukan · dialirkan ke ${ids.length} ruang` : 'Pemasukan · belum dialirkan',
      });
    }
    const msg = ids.length
      ? `${rp(A.amount - rest)} dialirkan ke ${ids.length} ruang${rest ? `, ${rp(rest)} belum dialirkan` : ''}`
      : `${rp(A.amount)} tersimpan, belum dialirkan`;
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
    if (!S.order.length) return smp;
    // Data pengguna hanya punya akun dan ruang miliknya: contoh disesuaikan ke yang ada.
    const fit = { acct: S.acct[smp.acct] ? smp.acct : S.acctOrder[0], room: S.rooms[smp.room] ? smp.room : S.order[0] };
    S.drafts.push(Object.assign({ id: 'd' + S.sampleIdx }, smp, fit));
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
    if (!S.order.includes(e.room)) {
      toast('Belum ada ruang untuk pengeluaran ini. Pakai pola Tiga hak di tab Ruang.');
      return;
    }
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
    back: () => {
      if (route.name === 'aturan' && ruleDirty()) askDiscard();
      else back();
    },
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
      if (p) startAlokasi(p.label, p.amount, p.id, null, p.counted);
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
      const id = S.acctOrder.includes(arg) ? arg : S.acctOrder[0];
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
      if (!S.rooms[K2.room]) {
        toast('Belum ada ruang untuk mencatat selisih. Pakai pola Tiga hak di tab Ruang.');
        return;
      }
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
      if (!S.order.length) {
        toast('Tambah ruang dulu supaya draf punya tujuan.');
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
    /* --- S06 tab Transfer */
    'tr-from': (arg) => {
      draft.from = arg;
      if (draft.to === arg || !draft.to) draft.to = otherAcct(arg);
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    'tr-to': (arg) => {
      draft.to = arg;
      screenEl.innerHTML = catatScreen();
      updateCatat();
    },
    'save-transfer': () => {
      const n = Number(draft.amount || 0);
      if (!n || !draft.to || draft.to === draft.from) return;
      const before = JSON.stringify(S);
      const from = S.acct[draft.from];
      const to = S.acct[draft.to];
      const t = { id: newTxId(), kind: 'transfer', date: 'Hari ini', title: `Transfer ke ${to.name}`, sub: `${from.name} → ${to.name}`, amt: n, room: null, acct: from.id, to: to.id, note: '' };
      S.tx.unshift(t);
      txEffect(t, 1);
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      draft = newDraft();
      stack = [];
      route = { name: 'denah' };
      render();
      toast(`${rp(n)} dipindahkan dari ${from.name} ke ${to.name}`, 'Urungkan');
    },

    /* --- S08 dan S09 */
    'tx-kind': (arg) => {
      route.kind = arg;
      render();
    },
    'open-tx': (arg) => {
      const t = S.tx.find((x) => x.id === arg);
      if (!t) return;
      TD = {
        id: t.id, kind: t.kind, amount: String(Math.abs(t.amt)), room: t.room, cat: t.cat, acct: t.acct,
        to: t.to || otherAcct(t.acct), source: t.source || 'Lainnya', note: t.note || '', fav: false,
      };
      go('txdetail', { id: arg });
    },
    'td-key': (arg) => {
      TD.amount = applyKey(TD.amount, arg);
      updateTxDetail();
    },
    'td-room': (arg) => {
      syncTd();
      TD.room = arg;
      const list = catsOf(S.rooms[arg]);
      TD.cat = list.length ? list[0].name : null;
      redrawTd();
    },
    'td-cat': (arg) => {
      syncTd();
      TD.cat = arg;
      redrawTd();
    },
    'td-acct': (arg) => {
      syncTd();
      TD.acct = arg;
      if (TD.kind === 'transfer' && TD.to === arg) TD.to = otherAcct(arg);
      redrawTd();
    },
    'td-to': (arg) => {
      syncTd();
      TD.to = arg;
      redrawTd();
    },
    'td-src': (arg) => {
      syncTd();
      TD.source = arg;
      redrawTd();
    },
    'td-fav': () => {
      syncTd();
      TD.fav = !TD.fav;
      redrawTd();
    },
    'td-save': () => {
      syncTd();
      const t = S.tx.find((x) => x.id === TD.id);
      const n = Number(TD.amount || 0);
      if (!t || !n) return;
      const before = JSON.stringify(S);
      txEffect(t, -1);
      t.note = TD.note;
      if (t.kind === 'expense') {
        t.amt = -n;
        t.room = TD.room;
        t.cat = TD.cat;
        t.acct = TD.acct;
        t.title = TD.note.trim() || TD.cat;
        t.sub = `${TD.cat} · ${S.rooms[TD.room].name}`;
        if (TD.fav && !S.favs.some((f) => f.name === t.title && f.amt === n)) {
          S.favs.push({ id: 'f' + (S.favs.length + 1) + '-' + n, name: t.title, amt: n, room: t.room, cat: t.cat, acct: t.acct });
        }
      } else if (t.kind === 'income') {
        t.amt = n;
        t.acct = TD.acct;
        t.source = TD.source;
        t.title = TD.note.trim() || TD.source;
        const ids = Object.keys(t.pcts).filter((id) => S.rooms[id]);
        t.alloc = split(n, t.pcts, ids).parts;
      } else {
        t.amt = n;
        t.acct = TD.acct;
        t.to = TD.to;
        t.title = `Transfer ke ${S.acct[TD.to].name}`;
        t.sub = `${S.acct[TD.acct].name} → ${S.acct[TD.to].name}`;
      }
      txEffect(t, 1);
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      back();
      toast('Perubahan disimpan.', 'Urungkan');
    },
    'td-delete': () => confirmSheet('Hapus transaksi ini?', 'Saldo akun dan jatah ruang menyesuaikan. Kamu masih bisa mengurungkannya sesaat setelahnya.', 'Hapus transaksi', 'td-delete-ok'),
    'td-delete-ok': () => {
      const t = S.tx.find((x) => x.id === TD.id);
      if (!t) return;
      const before = JSON.stringify(S);
      txEffect(t, -1);
      S.tx = S.tx.filter((x) => x.id !== t.id);
      closeSheet();
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      back();
      toast('Transaksi dihapus.', 'Urungkan');
    },

    /* --- S10 tambah dan arsip ruang */
    'tambah-ruang': () => {
      if (!S.pro && rooms().length >= 5) {
        openSheet('pro', 'tambah-ruang');
        return;
      }
      SH = { kind: 'mencukupi', icon: 'home' };
      roomAddSheet();
    },
    'sh-pick': (arg) => {
      const [f, v] = arg.split(':');
      SH[f] = v;
      $$(`[data-action="sh-pick"][data-arg^="${f}:"]`).forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.arg === arg)));
      if (f === 'kind' && ROOM_KINDS[v] && $('#sh-hint')) $('#sh-hint').textContent = ROOM_KINDS[v].hint;
    },
    'room-add-save': () => {
      const name = $('#sh-name').value.trim();
      if (!name) {
        toast('Nama ruang wajib diisi.');
        return;
      }
      const before = JSON.stringify(S);
      const k = ROOM_KINDS[SH.kind];
      const n = Object.keys(S.rooms).length + 1;
      const id = 'r' + n;
      S.rooms[id] = { id, name, slot: n, icon: SH.icon, kind: SH.kind, kindLabel: k.label, verb: k.verb, pct: 0, alloc: 0, used: 0, pos: [{ name: 'Umum', base: 1, used: 0 }] };
      S.order.push(id);
      closeSheet();
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      render();
      toast(`Ruang ${name} ditambahkan. Atur persentasenya di Aturan alokasi.`, 'Urungkan');
    },
    'arsip-ruang': (arg) =>
      confirmSheet(`Arsipkan ${S.rooms[arg].name}?`, 'Ruang tidak tampil di Denah dan tidak menerima alokasi baru. Riwayatnya tetap ada dan bisa dipulihkan.', 'Arsipkan ruang', 'arsip-ok', arg),
    'arsip-ok': (arg) => {
      const before = JSON.stringify(S);
      const name = S.rooms[arg].name;
      S.order = S.order.filter((x) => x !== arg);
      S.archived.push(arg);
      closeSheet();
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      goTab('ruang');
      toast(`${name} diarsipkan. Atur ulang aturan alokasi supaya total tetap 100%.`, 'Urungkan');
    },
    'open-arsip': () =>
      showSheet(
        `<div class="sheet__grab"></div><h2>Ruang diarsipkan</h2>
        <ul class="list">${S.archived.map((id) => `<li><div class="list-row">${roomIcon(S.rooms[id])}<span class="list-row__main"><p class="list-row__title">${esc(S.rooms[id].name)}</p></span>
          <button type="button" class="btn btn--tonal btn--small" data-action="arsip-pulih" data-arg="${id}">Pulihkan</button></div></li>`).join('')}</ul>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--text btn--block" data-action="close-sheet">Tutup</button></div>`,
        'Ruang diarsipkan'
      ),
    'arsip-pulih': (arg) => {
      S.archived = S.archived.filter((x) => x !== arg);
      S.order.push(arg);
      closeSheet();
      render();
      toast(`${S.rooms[arg].name} dipulihkan. Atur aturan alokasinya bila perlu.`);
    },

    /* --- S13 akun, kategori, favorit */
    'open-kelola': () => go('kelola', { tab: 'akun' }),
    'kelola-tab': (arg) => {
      route.tab = arg;
      render();
    },
    'acct-add': () => {
      SH = { kind: 'tunai' };
      acctSheet(null);
    },
    'acct-edit': (arg) => {
      SH = { id: arg };
      acctSheet(S.acct[arg]);
    },
    'acct-save': () => {
      const name = $('#sh-name').value.trim();
      if (!name) {
        toast('Nama akun wajib diisi.');
        return;
      }
      if (SH.id) {
        S.acct[SH.id].name = name;
      } else {
        const id = 'a' + (Object.keys(S.acct).length + 1);
        const bal = Number(($('#sh-bal').value || '0').replace(/\D/g, '') || 0);
        S.acct[id] = { id, name, kind: SH.kind || 'tunai', bal, days: 0 };
        S.acctOrder.push(id);
      }
      closeSheet();
      render();
      toast(SH.id ? 'Nama akun disimpan.' : `Akun ${name} ditambahkan.`);
    },
    'acct-recon': (arg) => {
      closeSheet();
      ACTIONS['open-koreksi'](arg);
    },
    'acct-archive': (arg) => {
      if (S.acctOrder.length < 2) {
        toast('Akun terakhir tidak bisa diarsipkan.');
        return;
      }
      S.acctOrder = S.acctOrder.filter((x) => x !== arg);
      closeSheet();
      render();
      toast('Akun diarsipkan. Riwayat transaksinya tetap ada.');
    },
    'cat-add': (arg) => {
      SH = { room: arg };
      showSheet(
        `<div class="sheet__grab"></div><h2>Kategori baru di ${esc(S.rooms[arg].name)}</h2>
        <p class="field-label"><label for="sh-name">Nama kategori</label></p><input id="sh-name" class="field-input" type="text" maxlength="30" autocomplete="off" />
        <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="cat-save">Tambah kategori</button>
          <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
        'Kategori baru'
      );
    },
    'cat-save': () => {
      const name = $('#sh-name').value.trim();
      if (!name) {
        toast('Nama kategori wajib diisi.');
        return;
      }
      S.rooms[SH.room].pos.push({ name, base: 1, used: 0 });
      closeSheet();
      render();
      toast(`Kategori ${name} ditambahkan.`);
    },
    'fav-del': (arg) => {
      S.favs = S.favs.filter((f) => f.id !== arg);
      render();
      toast('Favorit dihapus.');
    },

    /* --- F5 zakat: S14, S15, S17 */
    'open-zakat': () => go('zakat'),
    'toggle-giving': () => {
      S.zk.mode = S.zk.mode === 'percent' ? 'zakat' : 'percent';
      screenEl.innerHTML = zakatScreen();
    },
    'zk-sim': (arg) => {
      const st = zkStatus(S.zk);
      if (st === 'setup' || st === 'below') {
        toast('Isi harta sampai di atas nisab dulu.');
        return;
      }
      S.zk.state = arg;
      S.zk.elapsed = arg === 'done' ? S.zk.total : 117;
      screenEl.innerHTML = zakatScreen();
    },
    'open-harta': () => {
      H = { price: String(S.zk.price || ''), items: S.zk.items.map((i) => ({ ...i })) };
      go('harta');
    },
    'harta-add': (arg) => {
      const id = 'z' + Date.now();
      H.items.push(
        arg === 'deduct' ? { id, label: 'Hutang jangka pendek', kind: 'deduct', value: 0 }
          : arg === 'gold' ? { id, label: 'Emas', kind: 'gold', grams: 0, value: 0 }
            : { id, label: 'Harta lain', kind: 'other', value: 0 }
      );
      screenEl.innerHTML = hartaScreen();
      updateHarta();
    },
    'harta-del': (arg) => {
      H.items = H.items.filter((i) => i.id !== arg);
      screenEl.innerHTML = hartaScreen();
      updateHarta();
    },
    'harta-save': () => {
      const Z = hartaDraftZk();
      if (!Z.price) {
        toast('Isi harga emas dulu supaya nisab bisa dihitung.');
        return;
      }
      const before = JSON.stringify(S);
      const old = S.zk;
      const oldSt = zkStatus(old);
      const wasAbove = oldSt === 'running' || oldSt === 'done';
      const nz = Object.assign({}, old, { price: Z.price, items: Z.items, started: true, priceDate: '20 Sep 2026' });
      let msg = 'Profil harta disimpan.';
      if (zkNet(nz) < zkNisab(nz)) {
        nz.elapsed = 0;
        nz.state = 'running';
        msg = 'Harta di bawah nisab: dipantau, belum ada haul.';
      } else if (!wasAbove) {
        nz.elapsed = 0;
        nz.state = 'running';
        msg = `Harta mencapai nisab. Haul dimulai hari ini (${hijriStr(TODAY)}).`;
      }
      S.zk = nz;
      S.zakat = true;
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      back();
      toast(msg, 'Urungkan');
    },
    'open-tunai': () => {
      U = { amount: String(zkDue(S.zk)), acct: defAcct() };
      go('tunai');
    },
    'u-key': (arg) => {
      U.amount = applyKey(U.amount, arg);
      updateTunai();
    },
    'u-acct': (arg) => {
      U.acct = arg;
      screenEl.innerHTML = tunaiScreen();
      updateTunai();
    },
    'u-save': () => {
      const n = Number(U.amount || 0);
      if (!n || !S.rooms.memberi) return;
      const before = JSON.stringify(S);
      addExpense({ n, room: 'memberi', cat: 'Zakat mal', acct: U.acct, title: 'Zakat mal' });
      S.zk.payments.unshift({ date: '20 Sep 2026', hijri: hijriStr(TODAY), amt: n });
      S.zk.state = 'running';
      S.zk.elapsed = 0;
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      stack = [{ name: 'denah' }];
      route = { name: 'zakat' };
      render();
      toast(`Zakat ${rp(n)} ditunaikan. Haul baru dimulai.`, 'Urungkan');
    },

    /* --- S19 keamanan */
    'open-keamanan': () => go('keamanan'),
    'sec-pin': () => {
      if (S.sec.pinOn) {
        S.sec.pinOn = false;
        S.sec.bio = false;
        S.sec.pin = '';
        screenEl.innerHTML = keamananScreen();
        toast('Kunci aplikasi dimatikan.');
      } else {
        ACTIONS['pin-start']('buat');
      }
    },
    'sec-bio': () => {
      S.sec.bio = !S.sec.bio;
      screenEl.innerHTML = keamananScreen();
    },
    'sec-lock': (arg) => {
      S.sec.lock = arg;
      screenEl.innerHTML = keamananScreen();
    },
    'sec-hide': () => {
      S.sec.hide = !S.sec.hide;
      screenEl.innerHTML = keamananScreen();
    },
    'pin-start': (arg) => {
      PIN = { mode: arg, step: 1, first: '', cur: '', error: '' };
      go('pin');
    },
    'pin-cancel': () => back(),
    'pin-key': (arg) => {
      if (arg === 'back') PIN.cur = PIN.cur.slice(0, -1);
      else if (PIN.cur.length < 6) PIN.cur += arg;
      if (PIN.cur.length === 6) {
        if (PIN.step === 1) {
          PIN = Object.assign(PIN, { first: PIN.cur, cur: '', step: 2, error: '' });
        } else if (PIN.cur === PIN.first) {
          S.sec.pinOn = true;
          S.sec.pin = PIN.cur;
          const msg = PIN.mode === 'ubah' ? 'PIN diubah.' : 'PIN dibuat. Kunci aplikasi aktif.';
          back();
          toast(msg);
          return;
        } else {
          PIN = Object.assign(PIN, { step: 1, first: '', cur: '', error: 'PIN tidak sama. Coba lagi dari awal.' });
        }
      }
      screenEl.innerHTML = pinScreen();
    },
    'kunci-sim': () => {
      LOCK = { cur: '', error: '', tries: 0 };
      go('kunci');
    },
    'lock-key': (arg) => {
      if (arg === 'back') LOCK.cur = LOCK.cur.slice(0, -1);
      else if (LOCK.cur.length < 6) LOCK.cur += arg;
      if (LOCK.cur.length === 6) {
        if (LOCK.cur === S.sec.pin) {
          back();
          toast('Terbuka.');
          return;
        }
        LOCK = Object.assign(LOCK, { cur: '', tries: LOCK.tries + 1, error: 'PIN salah.' });
      }
      screenEl.innerHTML = kunciScreen();
    },
    'lock-bio': () => {
      back();
      toast('Terbuka dengan sidik jari (simulasi).');
    },

    /* --- F7 backup dan restore, S20 */
    'open-cadangan': () => go('cadangan'),
    'bk-make': () =>
      showSheet(
        `<div class="sheet__grab"></div><h2>Cadangkan data</h2>
        <p class="field-label"><label for="bk-pw">Sandi</label></p><input id="bk-pw" class="field-input" type="password" autocomplete="off" />
        <p class="field-label"><label for="bk-pw2">Ulangi sandi</label></p><input id="bk-pw2" class="field-input" type="password" autocomplete="off" />
        <p class="list-row__sub" style="margin:8px 0 0">Sandi tidak bisa dipulihkan. Tanpa sandi, cadangan tidak bisa dibuka.</p>
        <div class="actions" style="margin-top:var(--rf-space-4)"><button type="button" class="btn btn--primary btn--block" data-action="bk-save">Simpan dan bagikan</button>
          <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
        'Cadangkan data'
      ),
    'bk-save': () => {
      const a = $('#bk-pw').value;
      if (a.length < 6) {
        toast('Sandi minimal 6 karakter.');
        return;
      }
      if (a !== $('#bk-pw2').value) {
        toast('Kedua sandi harus sama.');
        return;
      }
      backupStore = { pw: a, when: '20 Sep 2026', snapshot: JSON.stringify(S) };
      closeSheet();
      render();
      toast('Cadangan terenkripsi tersimpan dan siap dibagikan (simulasi).');
    },
    'bk-restore': () => {
      if (!backupStore) {
        toast('Belum ada cadangan di prototipe. Buat cadangan dulu.');
        return;
      }
      showSheet(
        `<div class="sheet__grab"></div><h2>Pilih berkas cadangan</h2>
        <ul class="list"><li><button type="button" class="list-row" data-action="bk-file" data-arg="ok"><span class="list-row__main"><p class="list-row__title">rizqflow-2026-09-20.rfbak</p><p class="list-row__sub">Cadangan terakhir</p></span>${icon('chevron')}</button></li>
        <li><button type="button" class="list-row" data-action="bk-file" data-arg="bad"><span class="list-row__main"><p class="list-row__title">rusak.rfbak</p><p class="list-row__sub">Untuk mencoba berkas rusak</p></span>${icon('chevron')}</button></li></ul>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
        'Pilih berkas cadangan'
      );
    },
    'bk-file': (arg) => {
      RS = { file: arg };
      showSheet(
        `<div class="sheet__grab"></div><h2>Sandi cadangan</h2>
        <p class="field-label"><label for="rs-pw">Sandi</label></p><input id="rs-pw" class="field-input" type="password" autocomplete="off" />
        <p class="pin-error" id="rs-err" role="status"></p>
        <div class="actions" style="margin-top:var(--rf-space-3)"><button type="button" class="btn btn--primary btn--block" data-action="bk-pw">Lanjut</button>
          <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
        'Sandi cadangan'
      );
    },
    'bk-pw': () => {
      if (RS.file === 'bad' || $('#rs-pw').value !== backupStore.pw) {
        $('#rs-err').textContent = 'Sandi salah atau berkas rusak. Data sekarang tidak disentuh.';
        return;
      }
      confirmSheet('Timpa data sekarang?', 'Semua data di ponsel ini akan diganti isi cadangan. Ini tidak bisa diurungkan.', 'Timpa dan pulihkan', 'bk-apply');
    },
    'bk-apply': () => {
      S = JSON.parse(backupStore.snapshot);
      closeSheet();
      prevView = null;
      draft = newDraft();
      stack = [];
      route = { name: 'denah' };
      render();
      toast('Data dipulihkan dari cadangan.');
    },
    'csv-export': () =>
      showSheet(
        `<div class="sheet__grab"></div><h2>Ekspor transaksi</h2>
        <p class="list-row__sub" style="margin:0 0 var(--rf-space-4)">${S.tx.length} transaksi diekspor ke berkas CSV apa adanya. Selalu gratis.</p>
        <div class="actions"><button type="button" class="btn btn--primary btn--block" data-action="csv-go">Bagikan berkas CSV</button>
          <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Batal</button></div>`,
        'Ekspor transaksi'
      ),
    'csv-go': () => {
      closeSheet();
      toast(`Simulasi: ${S.tx.length} transaksi diekspor.`);
    },
    'open-impor': () => {
      IM = { picked: false };
      go('impor');
    },
    'impor-pick': () => {
      IM.picked = true;
      render();
    },
    'impor-go': () => {
      back();
      toast('Simulasi: impor tidak mengubah data prototipe.');
    },

    /* --- S22 dan S23 */
    'open-tampilan': () => go('tampilan'),
    'open-tentang': () => go('tentang'),
    'theme-set': (arg) => {
      setTheme(arg);
      screenEl.innerHTML = tampilanScreen();
    },
    'lang-set': (arg) => {
      if (arg === 'en') {
        toast('Bahasa Inggris dikerjakan di Tahap 6 (lokalisasi).');
        return;
      }
      LANG = arg;
      screenEl.innerHTML = tampilanScreen();
    },
    'demo-toggle': () => {
      if (S.demo) ACTIONS['exit-demo']();
      else ACTIONS['enter-demo']();
    },
    /* --- F1 onboarding (S01-S04) */
    'ob-start': () => go('pola'),
    'ob-template': (arg) => {
      OB.template = arg;
      screenEl.innerHTML = polaScreen();
    },
    'ob-next-pola': () => {
      if (OB.template === 'tiga') {
        R = { kind: 'ob', ids: TEMPLATE_IDS, rooms: TEMPLATE, pcts: OB.pcts, focus: null };
        go('persen');
      } else {
        go('akun');
      }
    },
    'ob-next-persen': () => go('akun'),
    'ob-type': (arg) => {
      OB.acctType = arg;
      if (!OB.nameTouched) OB.acctName = ACCT_TYPES.find((t) => t.id === arg).def;
      screenEl.innerHTML = akunScreen();
      updateOb();
    },
    'ob-key': (arg) => {
      OB.balance = applyKey(OB.balance, arg);
      updateOb();
    },
    'ob-done': () => {
      if (!OB.acctName.trim()) return;
      S = freshState(OB);
      const name = S.acct.akun1.name;
      const bal = S.acct.akun1.bal;
      draft = newDraft();
      prevView = null;
      R = null;
      stack = [];
      route = { name: 'denah' };
      render();
      toast(`Akun ${name} dibuat dengan saldo ${rp(bal)}.`);
    },
    'ob-restart': () => startOnboarding(),
    /** Pakai pola Tiga hak dari Denah, Ruang, atau Catat saat belum ada ruang. */
    'apply-template': () => {
      const before = JSON.stringify(S);
      const t = seed();
      TEMPLATE_IDS.forEach((id) => {
        const r = t.rooms[id];
        r.alloc = 0;
        r.used = 0;
        r.pos.forEach((p) => {
          p.used = 0;
        });
        S.rooms[id] = r;
      });
      S.order = TEMPLATE_IDS.slice();
      S.last = { room: 'keluarga', cat: 'Lain-lain' };
      if (route.name === 'catat') {
        draft.room = 'keluarga';
        draft.cat = null;
      }
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      render();
      toast('Pola Tiga hak dipakai: Memberi, Diri, dan Keluarga.', 'Urungkan');
    },
    'enter-demo': () => {
      realState = JSON.stringify(S);
      S = seed();
      draft = newDraft();
      prevView = null;
      stack = [];
      route = { name: 'denah' };
      render();
      toast('Mode demo: data contoh, terpisah dari datamu.');
    },
    'demo-info': () =>
      showSheet(
        `<div class="sheet__grab"></div>
        <h2>Mode demo</h2>
        <p class="list-row__sub" style="margin:0 0 var(--rf-space-4)">Kamu sedang melihat data contoh. Data ini terpisah dari datamu sendiri dan hilang saat kamu keluar.</p>
        <div class="actions">
          <button type="button" class="btn btn--primary btn--block" data-action="exit-demo">Keluar dari mode demo</button>
          <button type="button" class="btn btn--text btn--block" data-action="close-sheet">Tetap di mode demo</button>
        </div>`,
        'Mode demo'
      ),
    'exit-demo': () => {
      closeSheet();
      if (!realState) {
        startOnboarding();
        return;
      }
      S = JSON.parse(realState);
      realState = null;
      draft = newDraft();
      prevView = null;
      stack = [];
      route = { name: 'denah' };
      render();
      toast('Kembali ke datamu.');
    },

    /* --- F4 aturan alokasi (S12) */
    'open-aturan': (arg) => {
      R = {
        kind: 'ar', ids: S.order.slice(), rooms: S.rooms, focus: S.rooms[arg] ? arg : null,
        pcts: Object.fromEntries(S.order.map((id) => [id, S.rooms[id].pct])),
      };
      R.orig = Object.assign({}, R.pcts);
      go('aturan');
    },
    'rule-step': (arg) => {
      const [id, d] = arg.split(':');
      setRule(id, R.pcts[id] + Number(d));
    },
    'ar-save': () => {
      if (ruleSum() !== 100 || !ruleDirty()) return;
      const before = JSON.stringify(S);
      R.ids.forEach((id) => {
        S.rooms[id].pct = R.pcts[id];
      });
      R = null;
      undo = () => {
        S = JSON.parse(before);
        render();
      };
      back();
      toast('Aturan disimpan. Berlaku untuk pemasukan berikutnya.', 'Urungkan');
    },
    'ar-discard': () => {
      closeSheet();
      R = null;
      back();
    },
    'ar-pro': () => {
      if (S.pro) toast('Simulasi: aturan lanjutan belum dibuat di prototipe.');
      else openSheet('pro');
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

  document.addEventListener('input', (e) => {
    const t = e.target;
    if (!t || !t.dataset) return;
    if (t.id === 'tx-q') {
      route.q = t.value;
      $('#tx-list').innerHTML = txListHtml();
    } else if (t.dataset.hf && H) {
      const [id, f] = t.dataset.hf.split(':');
      if (id === 'price') {
        t.value = t.value.replace(/\D/g, '');
        H.price = t.value;
      } else {
        const it = H.items.find((i) => i.id === id);
        if (it) {
          if (f !== 'label') t.value = t.value.replace(/\D/g, '');
          it[f] = t.value;
        }
      }
      updateHarta();
    } else if (t.id === 'ob-name') {
      OB.acctName = t.value;
      OB.nameTouched = true;
      updateOb();
    } else if (t.dataset.rule && R) {
      setRule(t.dataset.rule, Number(t.value));
    }
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
        realState = null;
        OB = newOB();
        R = null;
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
  OB = newOB();
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
  } else if (['sambutan', 'pola', 'persen', 'akun'].includes(start)) {
    const seq = ['sambutan', 'pola', 'persen', 'akun'];
    if (q.get('template') === 'kosong') OB.template = 'kosong';
    R = { kind: 'ob', ids: TEMPLATE_IDS, rooms: TEMPLATE, pcts: OB.pcts, focus: null };
    route = { name: start };
    stack = seq.slice(0, seq.indexOf(start)).map((name) => ({ name }));
  } else if (start === 'kosong' || start === 'kosong-tanpa-ruang') {
    // Denah pengguna baru setelah onboarding: dengan pola Tiga hak, atau tanpa ruang sama sekali.
    OB.template = start === 'kosong' ? 'tiga' : 'kosong';
    S = freshState(OB);
    if (q.get('pro') === '1') S.pro = true;
    draft = newDraft();
    route = { name: 'denah' };
  } else if (start === 'aturan') {
    ACTIONS['open-aturan'](q.get('focus') || '');
    stack = [{ name: 'ruang' }];
  } else if (start === 'txdetail') {
    ACTIONS['open-tx'](q.get('id') || 't1');
    stack = [{ name: 'transaksi' }];
  } else if (start === 'harta') {
    ACTIONS['open-harta']();
    stack = [{ name: 'zakat' }];
  } else if (start === 'tunai') {
    S.zk.state = 'done';
    S.zk.elapsed = S.zk.total;
    ACTIONS['open-tunai']();
    stack = [{ name: 'zakat' }];
  } else if (start === 'pin') {
    ACTIONS['pin-start']('buat');
    stack = [{ name: 'keamanan' }];
  } else if (start === 'kunci') {
    S.sec = { pinOn: true, pin: '123456', bio: true, lock: 'segera', hide: true };
    ACTIONS['kunci-sim']();
    stack = [{ name: 'keamanan' }];
  } else if (start === 'impor') {
    ACTIONS['open-impor']();
    IM.picked = true;
    stack = [{ name: 'cadangan' }];
  } else if (start === 'kelola') {
    route = { name: 'kelola', tab: q.get('tab') || 'akun' };
    stack = [{ name: 'lainnya' }];
  } else if (SCREENS[start]) {
    if (start === 'zakat' && q.get('haul') === 'genap') {
      S.zk.state = 'done';
      S.zk.elapsed = S.zk.total;
    }
    route = { name: start };
    if (['zakat', 'keamanan', 'cadangan', 'tampilan', 'tentang'].includes(start)) stack = [{ name: 'lainnya' }];
  }
  render();
  if (start === 'pro') openSheet('pro');
  if (start === 'kilat') openKilat();
  if (start === 'notif') showNotif();
})();
