require.config({ paths: { 'vs': 'https://cdn.jsdelivr.net/npm/monaco-editor@0.22.3/min/vs' }});
window.MonacoEnvironment = { getWorkerUrl: () => proxy };
let proxy = URL.createObjectURL(new Blob([`
   self.MonacoEnvironment = {
      baseUrl: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.22.3/min'
   };
   importScripts('https://cdn.jsdelivr.net/npm/monaco-editor@0.22.3/min/vs/base/worker/workerMain.min.js');
`], { type: 'text/javascript' }));

require(["vs/editor/editor.main"], function () {
   monaco.editor.defineTheme('hf-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [{'background': '#000000'}],
      colors: {
          'editor.background': '#000000'
      }
   })
   window.editor = monaco.editor.create(document.getElementById('editor-content'), {
      value: 'name: foobar',
      language: 'yaml',
      theme: 'hf-dark',
      fontSize: "16px",
      fontFamily: '"Courier New", monospace',
      automaticLayout: true,
   });
   window.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KEY_S, () => saveEdits());
});