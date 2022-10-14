# AsyncWorld
An asynchronous world modification and in-memory world creation utility for Minecraft
## Installation
### Maven:
```xml
<repository>
    <id>UltraGrav</id>
    <url>https://mvn.ultragrav.net</url>
</repository>

<dependency>
    <groupId>net.ultragrav.async-world</groupId>
    <artifactId>async-world</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Usage
### Running as an API (Requires installing the AsyncWorld plugin)

### Running as a library
In your onEnable, initialize the chunk queue as follows
```java
new GlobalChunkQueue(this);
```

### Set blocks in a region
```java
//May be done asynchronously
AsyncWorld world = new SpigotAsyncWorld(bukkitWorld);
world.setBlocks(cuboidRegion, () -> (short) 0);
world.flush();

//May not be done asynchronously
AsyncWorld world = new SpigotAsyncWorld(bukkitWorld);
world.setBlocks(cuboidRegion, () -> (short) 0);
world.syncFlush();
```

### Create and save a schematic
```java
Schematic schematic = new Schematic(new IntVector3D(0, 0, 0), cuboidRegion);
schematic.save(file);
```

### Create in-memory world
```java
CustomWorld customWorld = new SpigotCustomWorld(plugin, "My World", 8, 8); //Plugin, Name, X Size in chunks, Z size in chunks

customWorld.create((world) -> {
  world.setBlocks(new CuboidRegion(null, new Vector3D(0, 0, 0), new Vector3D(1, 1, 1)), () -> (short) 1);
  //world.flush() is not needed and has no function here
});
```

### Saving in-memory world while loaded
```java
SavedCustomWorld saved = customWorld.getSavedCustomWorld(); //May be called asynchronously, the function will decide whether to post to main thread or not.
GravSerializer serializer = new GravSerializer();
saved.serialize(serializer);
serializer.save(file);
```

### Saving and unloading in-memory world
```java
SpigotCustomWorldAsyncWorld asyncWorld = customWorld.getAsyncWorld();

//unload must be called synchronously
customWorld.unload(); //Unloading removes reference to the asyncWorld so we store it in the line above
SavedCustomWorld saved = customWorld.getSavedCustomWorld(asyncWorld);
```
