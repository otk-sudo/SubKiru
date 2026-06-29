# searchTemplates テストデータ修正指示書（Codex向け）

> Play Store リリース前のシステムテスト（フェーズB: インストルメンテーションテスト）で
> 検出された **失敗テスト1件**を解消するための修正指示書。
> 実装は本書と `AGENTS.md` の両方に従うこと。

---

## 1. 背景・現象

フェーズB（`gradlew :app:connectedDebugAndroidTest`、エミュレータ Pixel_8）で
**32件中1件失敗**。

```
ServiceTemplateDaoTest > searchTemplatesで名前から検索できる  FAILED
java.lang.AssertionError: expected:<[1]> but was:<[1, 2]>
  ServiceTemplateDaoTest.kt:91 (テスト本体 :84)
```

## 2. 真因（重要：プロダクションコードは正しい）

- `ServiceTemplateDao.searchTemplates` の実装は
  **`name LIKE` OR `search_keywords LIKE`** で、名前・キーワード両方を検索する**正しい仕様**。

  ```sql
  SELECT * FROM service_templates
  WHERE name LIKE '%' || :query || '%'
      OR search_keywords LIKE '%' || :query || '%'
  ORDER BY name ASC
  ```

- 失敗テスト（`ServiceTemplateDaoTest.kt:83-92`）の**2件目のダミーデータが不備**:
  - 2件目は `name="Spotify"` だが、`searchKeywords` を上書きせず
    **デフォルト値 `"netflix,ネトフリ,動画"`（`SEARCH_KEYWORDS`）のまま**。
  - クエリ `"Net"`（`NAME_QUERY`）が、2件目の `search_keywords` 内の `"netflix"` にもマッチ
    → 意図せず `[1, 2]` が返る。
- **DB・アプリ機能に問題はない。テストコード側のデータ不備**。

## 3. 対象ファイル

- `app/src/androidTest/java/com/subkiru/subkiru/core/data/db/dao/ServiceTemplateDaoTest.kt`
- ⚠️ **プロダクションコード（`ServiceTemplateDao.kt` 等）は変更しない**。

## 4. 修正方針

失敗テストの2件目のダミーデータに、`Netflix` と無関係な `searchKeywords` を指定する。

```kotlin
// 修正前（87行目付近）
dao.addTemplate(template = template(name = OTHER_TEMPLATE_NAME, categoryId = categoryId))

// 修正後
dao.addTemplate(
    template = template(
        name = OTHER_TEMPLATE_NAME,
        searchKeywords = OTHER_SEARCH_KEYWORDS,
        categoryId = categoryId,
    )
)
```

companion object に定数を追加（マジック文字列回避・AGENTS.md準拠）:

```kotlin
private const val OTHER_SEARCH_KEYWORDS = "spotify,音楽,ストリーミング"
```

- これで `"Net"` が2件目にマッチしなくなり、結果は `[targetId]`（= `[1]`）のみになる。
- 「実装に合わせてテストを緩める」のではなく、**テストの前提データ(Arrange)の誤りを正す**修正。

## 5. 影響確認（他テストは修正不要）

- `searchTemplatesでキーワードから検索できる`: 既に `searchKeywords` を明示指定済み → 影響なし。
- `searchTemplatesで該当なしの場合は空リストを返す`: クエリ `"not_found"` はどのフィールドにも
  マッチしない → 影響なし。

## 6. 受け入れ基準

- [ ] `gradlew :app:connectedDebugAndroidTest` が **全32件グリーン**
- [ ] `gradlew :app:testDebugUnitTest` も引き続き成功
- [ ] プロダクションコードを変更していない

---

## 付録: システムテスト フェーズB 結果（2026-06-29 時点）

| 項目 | 値 |
|------|-----|
| 実行コマンド | `gradlew :app:connectedDebugAndroidTest` |
| 実行端末 | エミュレータ Pixel_8（API 35, google_apis_playstore, x86_64） |
| 結果 | 32件中 **31件成功 / 1件失敗** |
| 失敗 | `ServiceTemplateDaoTest.searchTemplatesで名前から検索できる`（本書で対応） |

本書の修正後、`connectedDebugAndroidTest` を再実行してフェーズBの完了を確認し、
フェーズC（署名設定 → リリースAAB生成 → 手動シナリオテスト）へ進む。
