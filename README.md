# Fake Players (Fabric 1.21.11)
- /fakeplayer spawn [count] | spawn name <name>
- /fakeplayer mass spawn <count> [random|square|lines [forward|facing]] [walk]
- /fakeplayer mass spawn <count> aisle [spacing] [rows] [gap]      two sides facing each other (default spacing 2, 1 row per side, gap 3)
- /fakeplayer mass spawn custom <left> <right> [spacing] [rows] [gap]   choose how many on each side
- /fakeplayer rank spawn <normal|general|commander> [count]
- /fakeplayer rank mass spawn <normal|general|commander> <count> [random|square|lines [forward|facing]] [walk]
- /fakeplayer look <north|south|east|west|northeast|northwest|southeast|southwest|player|same>
- /fakeplayer follow start|stop
- /fakeplayer inv <name>          open a bot's inventory (changes are saved as that rank's loadout)
- /fakeplayer loadout <rank>      edit a rank's gear without spawning anyone
- /fakeplayer loadout reset <rank>
- /fakeplayer head move|stop      bots look around with their heads
- /fakeplayer jump continue|stop|once
- /fakeplayer remove <name> | removeall | list
Config: config/fakeplayer.properties. Saved loadouts: config/fakeplayer_loadouts.json (shared by all worlds).


## Upgrade 1.1
- Up to **5000** fake players (config `max_fake_players`, default 5000). Big spawns are queued and spread over ticks so the server doesn't freeze (`spawn_ms_per_tick`).
- `/fakeplayer march 10s | 1m | <seconds> | stop` - all bots walk straight ahead (the way they face) for that long.
- `/fakeplayer battle start | stop` - bots look up a bit and swing at the air.
- `/fakeplayer head move | stop` - now moves the WHOLE body (small shuffles around their spot) and never turns to look around.
