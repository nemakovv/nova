# Nova (Android, sing-box)
Сборка в облаке: залейте проект в GitHub (ветка main) -> вкладка Actions -> Build APK -> скачайте artifact nova-debug-apk.
Ядро: sing-box (libbox v1.9.7) собирается в CI автоматически. Xray-core не подключён: sing-box покрывает vless/Reality, vmess, trojan, ss, hysteria2, tuic.
Если CI падает: откройте лог упавшего шага и пришлите ошибку.
Версия libbox закреплена в build.yml (SINGBOX_VERSION); если сигнатуры PlatformInterface в CoreVpnService не совпадут, правится именно этот файл.
Не готово: JSON/YAML-подписки, пинг, статистика трафика, release-подпись.
