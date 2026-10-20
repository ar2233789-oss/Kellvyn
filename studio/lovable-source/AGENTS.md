<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

- Keep native inference lifecycle and model-download validation in the Android brain layer, separate from MainActivity's approved layout, so brain corrections cannot redesign the interface.
- Pin the native model to an immutable upstream revision and validate exact size, SHA-256 and resumed ranges; keep the pure download policy covered by Android unit tests before APK packaging.
