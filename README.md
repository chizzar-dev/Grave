<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:0b1220,100:0e7490&height=110&section=header&text=Grave&fontSize=42&fontColor=22d3ee&fontAlignY=54&desc=Keep%20your%20items%20in%20a%20grave%20when%20you%20die&descSize=13&descColor=94a3b8&descAlignY=80" width="100%" alt="Grave" />

<p>
<img src="https://img.shields.io/github/v/release/chizzar-dev/Grave?style=flat&label=release&color=06b6d4&labelColor=0b1220" alt="release" />
<img src="https://img.shields.io/badge/Minecraft-1.8%20%E2%80%93%201.21.11-0891b2?style=flat&labelColor=0b1220" alt="Minecraft 1.8 - 1.21.11" />
<img src="https://img.shields.io/badge/Java-8%2B-155e75?style=flat&labelColor=0b1220&logo=openjdk&logoColor=22d3ee" alt="Java 8+" />
<a href="LICENSE"><img src="https://img.shields.io/github/license/chizzar-dev/Grave?style=flat&label=license&color=0e7490&labelColor=0b1220" alt="license" /></a>
</p>

</div>

Grave stores your dropped items in a protected grave with a hologram and countdown. Graves are saved to disk, so they survive a restart.

*Grave, oldugunde dusen esyalarini hologram ve geri sayimli, korumali bir mezarda tutar. Mezarlar diske kaydedilir, yeniden baslatmaya dayanir.*

## Features · Özellikler
- Ölüm yerinde sandık + üstünde çok satırlı **hologram** ve canlı **geri sayım**
- **Mezarlar `graves.yml` dosyasında saklanır** — sunucu yeniden başlasa bile eşyaların durur
- **Tecrübe de saklanır**, mezarı açınca geri verilir
- Mezar başkaları tarafından kırılamaz, patlamayla yok edilemez
- Sadece sahibi ya da herkes açabilsin — senin seçimin
- `/grave list` ile mezarlarının yerini ve kalan süresini gör
- Süre dolunca eşyalar yere dökülür ya da doğrudan sahibine iade edilir
- Boşluğa, lavın içine ya da blok içine düşersen mezar güvenli bir noktaya kurulur
- Oyuncu başına mezar limiti

## Installation · Kurulum
1. [Releases](https://github.com/chizzar-dev/Grave/releases/latest) sayfasından `Grave.jar` dosyasını indir.
2. Sunucunun `plugins/` klasörüne at.
3. Sunucuyu yeniden başlat.
4. `plugins/Grave/config.yml` dosyasından süreyi ve hologramı düzenle.

## Commands · Komutlar
| Komut | Açıklama | Yetki |
|-------|----------|-------|
| `/grave list` | Mezarlarını ve kalan sürelerini listeler | herkes |
| `/grave reload` | Ayarları yeniden yükler | `grave.reload` |

**Alias:** `/mezar`

## Permissions · Yetkiler
| Yetki | Açıklama | Varsayılan |
|-------|----------|------------|
| `grave.use` | Ölünce mezar oluşur | herkes |
| `grave.open.others` | Başkalarının mezarlarını açabilir | op |
| `grave.reload` | Configi yeniden yükler | op |

## Configuration · Ayarlar
| Anahtar | Açıklama |
|---------|----------|
| `duration` | Mezar kaç saniye durur |
| `keep-experience` | Tecrübe de saklansın mı |
| `only-owner` | Sadece sahibi açabilsin mi |
| `protect` | Mezar kırılmaya ve patlamaya karşı korunsun mu |
| `place-chest` / `chest-material` | Görsel sandık konsun mu, hangi blok |
| `max-graves-per-player` | Oyuncu başına mezar limiti (0 = sınırsız) |
| `return-to-owner-on-expire` | Süre dolunca eşyalar sahibine mi verilsin |
| `hologram.lines` | Hologram satırları |
| `hologram.height` / `line-spacing` | Hologram yüksekliği ve satır aralığı |

### Yer tutucular

`%player%` · `%player_in%` (ilgi ekli: *chizzar'ın*) · `%time%` · `%items%` · `%xp%` · `%x%` `%y%` `%z%` `%world%`

## Building · Derleme
```bash
mvn clean package
```
Çıktı · Output: `target/Grave.jar`

Her push [GitHub Actions](https://github.com/chizzar-dev/Grave/actions/workflows/build.yml) ile derlenir; `v*` etiketli sürümler jar'la birlikte [Releases](https://github.com/chizzar-dev/Grave/releases) sayfasına eklenir.
<br><sub>Every push is built by GitHub Actions; tagged `v*` releases attach the jar.</sub>

## License · Lisans
[MIT](LICENSE) — istediğin gibi kullan, değiştir, dağıt · use, modify and distribute freely

<div align="center"><sub>chizzar-dev · Minecraft plugins for 1.8 – 1.21.11</sub></div>
