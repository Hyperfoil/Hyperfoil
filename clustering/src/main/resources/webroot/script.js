const INTERRUPT_SIGNAL = "__HYPERFOIL_INTERRUPT_SIGNAL__"
const PAGER_MAGIC = "__HYPERFOIL_PAGER_MAGIC__\n"
const EDIT_MAGIC = "__HYPERFOIL_EDIT_MAGIC__\n"
const BENCHMARK_FILE_LIST = "__HYPERFOIL_BENCHMARK_FILE_LIST__\n"
const DOWNLOAD_MAGIC = "__HYPERFOIL_DOWNLOAD_MAGIC__"
const DIRECT_DOWNLOAD_MAGIC = "__HYPERFOIL_DIRECT_DOWNLOAD_MAGIC__\n";
const DIRECT_DOWNLOAD_END = "__HYPERFOIL_DIRECT_DOWNLOAD_END__\n";
const SESSION_START = "__HYPERFOIL_SESSION_START__\n";
const RAW_HTML_START = "__HYPERFOIL_RAW_HTML_START__"
const RAW_HTML_END = "__HYPERFOIL_RAW_HTML_END__"

const ansiUp = new AnsiUp();
const resultWindow = document.getElementById("result");
var logo = document.getElementById("logo");
const command = document.getElementById("command");
command.remove();
const warning = document.getElementById('warning')
const upload = document.getElementById("upload");
upload.remove();
const pager = document.getElementById("pager");
const editor = document.getElementById("editor");
const reconnecting = document.getElementById("reconnecting")
const tokenFrame = document.getElementById("token-frame");
tokenFrame.onload = () => {
   authToken = tokenFrame.contentDocument.body.innerText
}
document.onkeydown = event => defaultKeyDown(event)

const sessionId = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });

let socket = createWebSocket()

function createWebSocket() {
   const wsProtocol = window.location.protocol === "https:" ? "wss://" : "ws://"
   const s = new WebSocket(wsProtocol + window.location.host + "/?" + sessionId);
   s.onmessage = (event) => {
      addResultToWindow(event.data);
   };
   s.onclose = () => {
      command.remove();
      socket = createWebSocket();
   }
   s.onopen = () => {
      reconnecting.style.visibility = 'hidden'
      if (resultWindow.lastChild) {
         resultWindow.lastChild.appendChild(command)
      } else {
         resultWindow.appendChild(command)
      }
      command.focus()
   }
   s.onerror = (e) => {
      command.remove();
      reconnecting.style.visibility = 'visible'
   }
   return s;
}

function sendCommand(command) {
   socket.send(command);
}


command.addEventListener("keydown", (event) => {
   if (logo) {
      logo.classList.add("logo-exit")
      setTimeout(() => {
         if (logo) {
            logo.remove()
            logo = undefined
         }
      }, 2000)
   }
   if (event.key === "Enter") {
      event.preventDefault();
      sendCommand(command.value + '\n');
      command.value = "";
      warning.style.height = 0
   } else if (event.key === "Tab") {
      event.preventDefault();
      sendCommand(command.value + '\t');
      command.value = "";
   } else if (event.key === "Backspace" && command.selectionStart === 0) {
      command.remove();
      const lastLine = resultWindow.lastChild
      if (lastLine.lastChild && lastLine.lastChild.nodeType === Node.TEXT_NODE) {
         lastLine.lastChild.nodeValue = lastLine.lastChild.nodeValue.slice(0, -1)
      }
      lastLine.appendChild(command)
      command.focus();
      sendCommand('\b');
   } else if (event.key === "ArrowUp") {
      sendCommand('\033[A');
   } else if (event.key === "ArrowDown") {
      sendCommand('\033[B');
   } else if (event.key === "ArrowLeft" && command.selectionStart === 0) {
      command.remove()
      const lastLine = resultWindow.lastChild
      if (lastLine.lastChild && lastLine.lastChild.nodeType === Node.TEXT_NODE) {
         let lastChar = lastLine.lastChild.nodeValue.slice(-1);
         command.value = lastChar + command.value;
         lastLine.lastChild.nodeValue = lastLine.lastChild.nodeValue.slice(0, -1);
         sendCommand('\b');
         command.selectionStart = 0
      }
      lastLine.appendChild(command)
      command.focus()
   } else if (event.key === "Escape" || (event.key == 'c' && event.ctrlKey)) {
      event.preventDefault();
      command.remove();
      resultWindow.lastChild.innerHTML += '<span class="ctrl-c">' + command.value + "</span>"
      command.value = ""
      warning.style.height = 0
      resultWindow.appendChild(command)
      sendCommand(INTERRUPT_SIGNAL)
   }
});

function checkCommand() {
   if (command.value.startsWith('upload') && !command.value.trim().endsWith('upload')) {
      warning.innerText = "Benchmark filename cannot be passed as an argument; use 'upload' without arguments."
      // we can't set size to 'auto', so the message must be single-line:
      // see https://css-tricks.com/using-css-transitions-auto-dimensions/
      warning.style.height = '1.2em';
   } else {
      warning.style.height = 0;
   }
}

var started = false;
var authToken;
var authSent = false;
var benchmarkForm = undefined;
var benchmarkVersion = ""
var paging = false;
var editing = false;
var fileList = "";
var receivingFileList = false;
var downloading = false;
var downloadContent = ""

function addResultToWindow(commandResult) {
   if (!authSent && authToken) {
      sendCommand("__HYPERFOIL_AUTH_TOKEN__" + authToken);
      authSent = true;
   }
   command.remove();
   while (true) {
      if (typeof commandResult !== 'string') {
         break;
      } else if (commandResult.startsWith('\u001b[160D')) {
         // arrow up, ignore
         commandResult = commandResult.slice(6);
      } else if (commandResult.startsWith('\u001b[2K') || commandResult.startsWith('\u001b[K')) {
         commandResult = commandResult.slice(commandResult.indexOf('K') + 1);
         resultWindow.lastChild.innerHTML = ""
      } else if (commandResult.startsWith('\u001b[1A')) {
         commandResult = commandResult.slice(4);
         resultWindow.lastChild.remove()
      } else {
          break
      }
   }
   if (paging) {
      document.getElementById('pager-content').innerHTML += commandResult;
   } else if (editing) {
      window.editor.setValue(window.editor.getValue() + commandResult)
   } else if (receivingFileList) {
      fileList += commandResult
      checkFileList()
   } else if (downloading) {
      if (typeof commandResult === 'string') {
         let endIndex = commandResult.indexOf(DIRECT_DOWNLOAD_END)
         if (endIndex >= 0) {
            downloadMeta += commandResult.slice(0, endIndex)
            let lineEnd = downloadMeta.indexOf('\n')
            let downloadFilename = downloadMeta.slice(0, lineEnd)
            download(window.URL.createObjectURL(downloadContent), downloadFilename)
            downloadMeta = undefined
            downloadContent = undefined
            downloading = false
            addResultToWindow(commandResult.slice(endIndex + DIRECT_DOWNLOAD_END.length))
         } else {
            downloadMeta += commandResult
         }
      } else if (commandResult instanceof Blob) {
         downloadContent = commandResult;
      }
   } else if (commandResult.startsWith("__HYPERFOIL_UPLOAD_MAGIC__")) {
      resultWindow.appendChild(upload)
   } else if (commandResult.startsWith(PAGER_MAGIC)) {
      commandResult = commandResult.slice(PAGER_MAGIC.length);
      paging = true;
      document.getElementById('pager-content').innerHTML = commandResult;
      pager.style.visibility = 'visible'
      pager.focus()
      document.onkeydown = event => {
         event = event || window.event
         if (paging && (event.key === 'Escape' || event.key === 'q')) stopPaging();
      }
   } else if (commandResult.startsWith(EDIT_MAGIC)) {
      commandResult = commandResult.slice(EDIT_MAGIC.length);
      command.remove()
      editing = true;
      editor.style.visibility = 'visible'
      window.editor.setValue(commandResult)
      window.editor.focus()
   } else if (commandResult.startsWith(BENCHMARK_FILE_LIST)) {
      fileList = commandResult.slice(BENCHMARK_FILE_LIST.length)
      receivingFileList = true
      checkFileList()
   } else if (commandResult.startsWith(DOWNLOAD_MAGIC)) {
      let parts = commandResult.split(' ')
      download(window.location + parts[1], parts[2])
      resultWindow.appendChild(command)
      command.focus();
   } else if (commandResult.startsWith(DIRECT_DOWNLOAD_MAGIC)) {
      downloading = true;
      downloadMeta = commandResult.slice(DIRECT_DOWNLOAD_MAGIC.length)
      downloadContent = undefined
   } else if (commandResult.startsWith(SESSION_START)) {
      if (started) {
         resultWindow.innerHTML += '<span class="line" style="color: yellow">Warning: Controller has been restarted.</span><span class="line"></span>'
         command.value = ''
      } else {
         started = true;
      }
   } else {
      let output = commandResult
      let html = ""
      let rawIndex = output.indexOf(RAW_HTML_START)
      while (rawIndex >= 0) {
         html += ansiUp.ansi_to_html(output.slice(0, rawIndex))
         const endIndex = output.indexOf(RAW_HTML_END, rawIndex)
         html += output.slice(rawIndex + RAW_HTML_START.length, endIndex)
         output = output.slice(endIndex + RAW_HTML_END.length)
         rawIndex = output.indexOf(RAW_HTML_START)
      }
      html += ansiUp.ansi_to_html(output)
      const lines = html.split('\n')
      var firstLine = 0;
      var lastLine = resultWindow.lastChild
      // when last node is text, it's a newline
      if (lastLine && lastLine.nodeType !== Node.TEXT_NODE) {
         lastLine.innerHTML += lines[0];
         firstLine = 1;
      }
      for (var i = firstLine; i < lines.length; ++i) {
         lastLine = document.createElement("span")
         lastLine.classList.add("line")
         lastLine.innerHTML = lines[i]
         resultWindow.appendChild(lastLine)
      }
      lastLine.appendChild(command)
      command.focus();
   }
}

function defaultKeyDown(event) {
   if (upload.parentNode && event.key === "Escape") {
      upload.remove();
      sendCommand(INTERRUPT_SIGNAL);
   }
   if (!event.ctrlKey && !event.altKey) {
      command.focus();
   }
}

function download(url, filename) {
   const a = document.createElement('a');
   a.href = url;
   a.download = filename;
   a.click();
}

function sendBenchmarkForFiles() {
   benchmarkForm = new FormData()
   benchmarkVersion = ""
   const benchmark = document.getElementById('upload-benchmark').files[0]
   benchmarkForm.append('benchmark', benchmark)
   benchmark.text().then(content => sendEdits(content))
   upload.remove()
}

function stopPaging() {
   document.getElementById('pager-content').innerHTML = ""
   pager.style.visibility = 'hidden'
   paging = false
   document.onkeydown = event => defaultKeyDown(event)
   sendCommand(INTERRUPT_SIGNAL)
}

function saveEdits() {
   editing = false;
   let editedBenchmark = window.editor.getValue()
   benchmarkForm = new FormData()
   benchmarkForm.append('benchmark', new Blob([editedBenchmark]), "benchmark.hf.yaml")
   sendEdits(editedBenchmark)
   window.editor.setValue("");
   editor.style.visibility = 'hidden'
}

function sendEdits(benchmark) {
   sendCommand('__HYPERFOIL_EDITS_BEGIN__\n')
   sendCommand(benchmark)
   sendCommand('__HYPERFOIL_EDITS_END__\n')
}

function checkFileList() {
   let endOfFiles = fileList.indexOf('__HYPERFOIL_BENCHMARK_END_OF_FILES__\n')
   if (endOfFiles < 0) {
      return
   }
   let lines = fileList.slice(0, endOfFiles).split('\n')
   receivingFileList = false
   fileList = "";

   let benchmark = lines[0]
   benchmarkVersion = lines[1]
   if (addUploadFiles(lines.slice(2))) {
      uploadBenchmark()
   }
}

function addUploadFiles(files) {
   let uploadEntries = document.createElement('div')
   uploadEntries.id = "upload-entries"
   resultWindow.appendChild(uploadEntries)
   var numFiles = 0;
   for (var i = 0; i < files.length; ++i) {
      if (files[i] === "") continue;
      ++numFiles
      uploadEntries.innerHTML += `<label class="hfbutton" style="margin: 2px 0 2px 0">
          <input class="hidden-upload" type="file" onchange="addUploadFile('${files[i]}', this)">
          ${files[i]}: <span id="filename">(not uploading)</span>
      </label>\n`
   }
   if (numFiles > 0) {
      uploadEntries.innerHTML += '<input type="button" class="hfbutton" value="Upload" onclick="uploadBenchmark()" />'
      return false
   }
   return true;
}

function addUploadFile(name, fileInput) {
   benchmarkForm.delete(name)
   benchmarkForm.append(name, fileInput.files[0], name)
   let siblings = fileInput.parentNode.childNodes
   for (var i = 0; i < siblings.length; ++i) {
      if (siblings[i].id === "filename") {
          siblings[i].innerHTML = fileInput.files[0].name
          break
      }
   }
}

function uploadBenchmark() {
   var headers = {};
   if (benchmarkVersion && benchmarkVersion !== "") {
      headers["if-match"] = benchmarkVersion;
   }
   resultWindow.innerHTML += "Uploading... "
   return fetch(window.location + "/benchmark", {
      method: 'POST',
      headers: Object.assign(headers, { Authorization: 'Bearer ' + authToken }),
      body: benchmarkForm,
   }).then(res => {
      if (res.ok) {
          resultWindow.innerHTML += " done.\n"
          const location = res.headers.get('Location')
          const name = location.slice(location.lastIndexOf('/') + 1)
          sendCommand("__HYPERFOIL_SET_BENCHMARK__" + name)
      } else {
          return res.text().then(error => {
              resultWindow.innerHTML += '\n<span style="color: red">' + error + '</span>\n'
          })
      }
   }, error => {
      resultWindow.innerHTML += error
   }).finally(() => {
      benchmarkForm = undefined
      benchmarkVersion = ""
      document.getElementById("upload-entries").remove()
      sendCommand(INTERRUPT_SIGNAL)
   })
}


function cancelEdits() {
   editing = false;
   window.editor.setValue("");
   editor.style.visibility = 'hidden'
   resultWindow.appendChild(command)
   command.focus();
   sendCommand(INTERRUPT_SIGNAL)
}

function resizeFrame(self) {
   self.style.height = self.contentWindow.document.documentElement.scrollHeight + "px";
   setTimeout(() => window.scrollTo(0, document.body.scrollHeight), 100)
}

function togglePlot(self) {
   const plot = self.previousSibling;
   if (self.textContent === 'Collapse') {
      plot.style.maxHeight = self.offsetHeight
      plot.style.opacity = '50%'
      self.textContent = 'Expand'
   } else {
      plot.style.maxHeight = 'unset'
      plot.style.opacity = '100%'
      self.textContent = 'Collapse'
   }
}
