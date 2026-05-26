# Data層 RepositoryImpl レビュー指摘事項（Codex向け）

## 対象ファイル
- `docs/data-layer-task.md` の設計内容に対するレビュー

---

## W-1: `addSubscription` で `id = 0L` の前提が未明示

### 現状
`Subscription.toEntity()` は `id` をそのまま Entity にマッピングする。
新規追加時に `id = 0L` であれば `autoGenerate = true` で正しく採番される。
しかし既存の `Subscription`（`id != 0`）を誤って `addSubscription` に渡すと、
`OnConflictStrategy.ABORT` により DB エラーが発生する。

### 修正内容
タスク1の注意点に以下を追記すること:

```
- 新規追加時の Subscription.id は 0L とする（SubscriptionEntity.UNSAVED_ID と一致）
- 既存 Subscription（id != 0）を addSubscription に渡さないこと（UseCase 側で制御）
```

---

## W-2: `deleteSubscription` が Domain Model 全体を引数に取る設計

### 現状
DAO の `@Delete` は Entity の全フィールドでマッチングする。
Domain → Entity 変換時にフィールドが1つでもずれると削除が失敗する（0行削除で silent failure）。
`id` だけで削除する `@Query` ベースの方が安全。

### 判断
Domain の `SubscriptionRepository` interface が `deleteSubscription(subscription: Subscription)` と定義済みのため、RepositoryImpl 単体では変更できない。**将来の改善候補として記録のみ。MVP段階では対応不要。**
