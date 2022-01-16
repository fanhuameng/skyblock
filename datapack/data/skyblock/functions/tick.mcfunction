execute as @a at @s if data entity @s {Dimension:"minecraft:overworld"} if score @s just_respawned matches 1 unless data entity @s SpawnY run function skyblock:nether_respawn

execute as @a[tag=respawned] at @s run spreadplayers ~ ~ 0 10 under 80 true @s
tag @a[tag=respawned] remove respawned

scoreboard objectives remove spawn.X
scoreboard objectives remove spawn.Z

execute as @a at @s unless score @s init matches 1 run function skyblock:init