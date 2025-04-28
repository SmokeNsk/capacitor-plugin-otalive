# capacitor-plugin-otalive

Capacitor App Live Update plugin

## Install

```bash
npm install capacitor-plugin-otalive
npx cap sync
```

## API

<docgen-index>

* [`applyUpdate()`](#applyupdate)
* [`rollBackUpdate()`](#rollbackupdate)
* [`checkpoint(...)`](#checkpoint)
* [`addListener('newVersionAvailable', ...)`](#addlistenernewversionavailable-)
* [`addListener('updateFailed', ...)`](#addlistenerupdatefailed-)
* [`addListener('updateSuccess', ...)`](#addlistenerupdatesuccess-)
* [`addListener('updateRolledBack', ...)`](#addlistenerupdaterolledback-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### applyUpdate()

```typescript
applyUpdate() => Promise<void>
```

--------------------


### rollBackUpdate()

```typescript
rollBackUpdate() => Promise<void>
```

--------------------


### checkpoint(...)

```typescript
checkpoint(data: CheckpointData) => Promise<void>
```

| Param      | Type                                                      |
| ---------- | --------------------------------------------------------- |
| **`data`** | <code><a href="#checkpointdata">CheckpointData</a></code> |

--------------------


### addListener('newVersionAvailable', ...)

```typescript
addListener(eventName: 'newVersionAvailable', listenerFunc: (data: { version: string; description: string; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                      |
| ------------------ | ------------------------------------------------------------------------- |
| **`eventName`**    | <code>'newVersionAvailable'</code>                                        |
| **`listenerFunc`** | <code>(data: { version: string; description: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('updateFailed', ...)

```typescript
addListener(eventName: 'updateFailed', listenerFunc: (data: { error: string; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                               |
| ------------------ | -------------------------------------------------- |
| **`eventName`**    | <code>'updateFailed'</code>                        |
| **`listenerFunc`** | <code>(data: { error: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('updateSuccess', ...)

```typescript
addListener(eventName: 'updateSuccess', listenerFunc: (data: object) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                   |
| ------------------ | -------------------------------------- |
| **`eventName`**    | <code>'updateSuccess'</code>           |
| **`listenerFunc`** | <code>(data: object) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('updateRolledBack', ...)

```typescript
addListener(eventName: 'updateRolledBack', listenerFunc: (data: object) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                   |
| ------------------ | -------------------------------------- |
| **`eventName`**    | <code>'updateRolledBack'</code>        |
| **`listenerFunc`** | <code>(data: object) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### CheckpointData

| Prop                | Type                |
| ------------------- | ------------------- |
| **`name`**          | <code>string</code> |
| **`executionTime`** | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

</docgen-api>
