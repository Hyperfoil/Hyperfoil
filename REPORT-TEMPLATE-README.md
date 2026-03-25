# Hyperfoil Report Template

## Overview

This document explains the Hyperfoil report template system, which generates HTML reports for benchmark runs. The report template is a React-based single-page application that displays performance metrics and statistics.

## Project Structure

### Source Project
- **Location**: `/path/to/Hyperfoil/report`
- **Type**: React-based web application
- **Build Output**: `report/build/index.embedded.html`

### Template Storage
- **Primary Location**: `clustering/src/main/resources/report-template-v3.3.html`
- **Historical Versions**:
  - `clustering/src/main/resources/report-template-v3.0.html`
  - `clustering/src/main/resources/report-template-v3.1.html`
  - `clustering/src/main/resources/report-template-v3.2.html`
  - `clustering/src/main/resources/report-template.html` (legacy)

### Usage in Code
- **Java Class**: `clustering/src/main/java/io/hyperfoil/clustering/ControllerServer.java`
- **Method**: `createReport(RoutingContext ctx, String runId, String source)`
- **Loading**: Template is loaded from classpath at runtime

### Distribution
- **Build Script**: `distribution/src/assembly/package.xml`
- **Output**: Copies template to `distribution/templates/report-template.html`

## Building the Report Template

### Prerequisites
- Node.js v16.x (required for compatibility)
- npm or yarn package manager
- nvm (Node Version Manager) recommended for version switching

### Build Steps

1. **Navigate to the report project**:
   ```bash
   cd /path/to/Hyperfoil/report
   ```

2. **Switch to Node 16** (if using nvm):
   ```bash
   nvm use 16
   ```

3. **Install dependencies** (first time only):
   ```bash
   npm install
   # or
   yarn install
   ```

4. **Build the template**:
   ```bash
   npm run build
   # or
   yarn run build
   ```

5. **Verify the output**:
   ```bash
   ls -lh build/index.embedded.html
   ```

### Build Output
- **File**: `report/build/index.embedded.html`
- **Type**: Minified, self-contained HTML file
- **Size**: Typically several hundred KB (includes bundled React and dependencies)
- **Content**: Complete single-page application with embedded CSS and JavaScript

## Updating the Template

### Update Process

1. **Build the new template** (follow build steps above)

Observation: Replace `report-template-v3.3.html` with the next version.

2. **Copy to Hyperfoil project**:
   ```bash
   cp /path/to/Hyperfoil/report/build/index.embedded.html \
      /path/to/Hyperfoil/clustering/src/main/resources/report-template-v3.3.html
   ```

3. **Update Java code** in `ControllerServer.java`:
   ```java
   // Change the template filename in the createReport method
   FileLookupFactory.newInstance().lookupFile("report-template-v3.3.html", ...)
   ```

4. **Update distribution script** in `distribution/src/assembly/package.xml`:
   ```xml
   <copy tofile="${dist.dir}/templates/report-template.html" failonerror="true">
       <fileset dir="${root.dir}/clustering/src/main/resources">
           <include name="report-template-v3.3.html"/>
       </fileset>
   </copy>
   ```
