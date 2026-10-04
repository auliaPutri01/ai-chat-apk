# Riwayat Perubahan

Catatan ringkas setiap perubahan yang digabungkan ke `main`. Entri terbaru di paling atas.
Semua waktu memakai UTC. Pelaksana: **Arena AI**.

---

## 2026-10-04 — feat(ui): antarmuka simpel ala Gemini, logo AI, hapus Maps Grounding

- **PR:** [#4 — feat(ui): antarmuka simpel ala Gemini, logo AI, hapus Maps Grounding](https://github.com/auliaPutri01/ai-chat-apk/pull/4)
- **Branch:** `feature/ui-sederhana` (dari `main` = `c28cc85`)
- **Push di branch:**
  - `d6eae04` — refactor: hapus Maps Grounding dari data dan UI (9 berkas)
  - `9d1f938` — feat(ui): antarmuka simpel ala Gemini, logo AI, warna dari tema
  - `0840a7c` — style(ui): rapikan kontras, target sentuh, dan perilaku gulir
  - `cc2ee23` — ci: build UI sederhana (commit pemicu build)
- **Jumlah berkas/baris:** 40 berkas berubah, +1187 / −1239 (hanya `app/src/main/java/com/example/ui/**` dan `app/src/main/res/**`)
- **Hasil CI branch:**
  - run [37188931139](https://github.com/auliaPutri01/ai-chat-apk/actions/runs/37188931139) — **SUCCESS** (push, commit `cc2ee23`)
  - run [37189095954](https://github.com/auliaPutri01/ai-chat-apk/actions/runs/37189095954) — **SUCCESS** (pull_request, commit `cc2ee23`)
  - run [37189113395](https://github.com/auliaPutri01/ai-chat-apk/actions/runs/37189113395) — **SUCCESS** (workflow_dispatch pada commit `48bad2d`, yaitu kepala branch terakhir sebelum merge)
- **SHA merge:** `8eabd8a` — squash merge PR #4 pada 2026-10-04T08:32:59Z
- **CI setelah merge (main):** run [37189284602](https://github.com/auliaPutri01/ai-chat-apk/actions/runs/37189284602) — **SUCCESS**
- **Tag rilis:** [`build-40`](https://github.com/auliaPutri01/ai-chat-apk/releases/tag/build-40) (prerelease, 2026-10-04T08:35:20Z) — aset `app-debug.apk` 21.681.736 B
- **Tag cadangan:** `backup/main-sebelum-ui-20261004-0829` → sha `c28cc85` (dipush sebelum merge)
- **Pelaksana:** Arena AI

**Isi ringkas:** antarmuka dibuat sesederhana mungkin ala Gemini chat — top bar minimal (menu, chip
profil+model, "+"), layar sambutan dengan logo "AI" dan tiga chip saran, satu pill input dengan sheet
"Alat Gemini", pesan pengguna berupa gelembung tonal dan balasan asisten tanpa gelembung, drawer
dengan pencarian dan kelompok Hari ini/Kemarin/Sebelumnya, semuanya memakai satu warna aksen dari
tema (dynamic color Android 12+, cadangan netral). Logo "AI" juga menjadi ikon peluncur (vektor,
termasuk monochrome). Seluruh jejak Maps Grounding dihapus dari data dan UI tanpa menghapus fitur
lain. Verifikasi lokal: 0 error / 0 warning, 207 tes lulus.

---
