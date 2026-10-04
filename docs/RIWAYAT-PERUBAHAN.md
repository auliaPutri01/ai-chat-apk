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
- **Hasil CI branch:** run [37188931139](https://github.com/auliaPutri01/ai-chat-apk/actions/runs/37188931139) — **SUCCESS** (commit `cc2ee23`)
- **SHA merge:** _diisi setelah merge_
- **Tag rilis:** _diisi setelah merge_
- **Tag cadangan:** `backup/main-sebelum-ui-20261004-0829` → sha `c28cc85`
- **Pelaksana:** Arena AI

**Isi ringkas:** antarmuka dibuat sesederhana mungkin ala Gemini chat — top bar minimal (menu, chip
profil+model, "+"), layar sambutan dengan logo "AI" dan tiga chip saran, satu pill input dengan sheet
"Alat Gemini", pesan pengguna berupa gelembung tonal dan balasan asisten tanpa gelembung, drawer
dengan pencarian dan kelompok Hari ini/Kemarin/Sebelumnya, semuanya memakai satu warna aksen dari
tema (dynamic color Android 12+, cadangan netral). Logo "AI" juga menjadi ikon peluncur (vektor,
termasuk monochrome). Seluruh jejak Maps Grounding dihapus dari data dan UI tanpa menghapus fitur
lain. Verifikasi lokal: 0 error / 0 warning, 207 tes lulus.

---
