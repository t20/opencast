<div class="layout-page-content">

  <div id="stage" class="layout-centered form-box" style="background:white; margin-top: 10px;">
    <div class="layout-centered">
      <center><h2><span id="i18n_page_title">Edit MediaPackage</span></h2></center>

    </div>

    <!-- missing fields notification -->

    <div id="missingFieldsContainer" class="layout-page-header ui-helper-hidden">
      <div class="layout-centered ui-state-error ui-corner-all scheduler-info-container">
        <h3 style="position: relative; padding-left: 20px;">
          <span class="ui-icon ui-icon-alert"></span> <b>Missing or invalid input</b>
        </h3>

        <ul>
          <li id="item-title" class="missing-fields-item">
            <span class="ui-icon ui-icon-carat-1-e"></span>
            <span>
              Please enter a <a href="javascript:document.uploadForm.title.focus();">title</a> for the recording.
            </span>
          </li>
          <li id="item-track" class="missing-fields-item">

            <span class="ui-icon ui-icon-carat-1-e"></span>
            <span>
              Please select a <a href="javascript:document.getElementById('track').focus();">media file</a> to upload. Supported file formats are: .avi, .mp3, .mp4, .mov, .mpg, .mkv, .flv or .wmv.
            </span>
          </li>
          <li id="item-recordDate" class="missing-fields-item">
            <span class="ui-icon ui-icon-carat-1-e"></span>
            <span>
              Please select a <a href="javascript:document.uploadForm.recordDate.focus();">recording date</a> for the recording.
            </span>
          </li>
        </ul>
      </div>
    </div>

    <form id="editForm" name="editForm" action="../ingest/addMediaPackage" method="POST" enctype="multipart/form-data" accept-charset="UTF-8">

      <div class="form-box layout-centered ui-widget">
        <div class="form-box-content ui-widget-content ui-corner-all">
          <form action="">
            <ul class="oc-ui-form-list">
              <li>
                <label class="scheduler-label" for="titleField" id="titleLabel"><span style="color: red;">* </span><span id="i18n_title_label">Title</span>:</label>

                <input type="text" id="titleField" name="title" class="oc-ui-form-field requiredField dc-metadata-field" maxlength="255" />
              </li>
              <li>
                <label class="scheduler-label" for="creator" id="creatorLabel"><span id="i18n_presenter_label">Presenter</span>:</label>
                <input type="text" id="creator" name="creator" class="oc-ui-form-field dc-metadata-field test"  maxlength="255"/>
              </li>
              <li id="seriesContainer">
                <label class="scheduler-label" for="series" id="seriesLabel"><span id="i18n_series_label">Course/Series</span>:</label>

                <input type="text" class="oc-ui-form-field" id="series" name="series" maxlength="255">
                <input type="hidden" class="oc-ui-form-field dc-metadata-field" id="isPartOf" name="isPartOf" value="">
              </li>
              <li>
                <label class="scheduler-label" id="recordingDateLabel"><span class="scheduler-required-text">* </span><span id="i18n_date_label">Recording Date</span>:</label>
                <input type="text" size="10" id="recordDate" name="recordDate" class="dc-metadata-field" />
              </li> 
              <li>

                <label class="scheduler-label" id="startTimeLabel"><span class="scheduler-required-text">* </span><span id="i18n_starttime_label">Start Time</span>:</label>
                <select id="startTimeHour">
                  <option value="0">00</option>
                  <option value="1">01</option>
                  <option value="2">02</option>
                  <option value="3">03</option>

                  <option value="4">04</option>
                  <option value="5">05</option>
                  <option value="6">06</option>
                  <option value="7">07</option>
                  <option value="8">08</option>
                  <option value="9">09</option>

                  <option value="10">10</option>
                  <option value="11">11</option>
                  <option value="12">12</option>
                  <option value="13">13</option>
                  <option value="14">14</option>
                  <option value="15">15</option>

                  <option value="16">16</option>
                  <option value="17">17</option>
                  <option value="18">18</option>
                  <option value="19">19</option>
                  <option value="20">20</option>
                  <option value="21">21</option>

                  <option value="22">22</option>
                  <option value="23">23</option>
                </select>
                <select id="startTimeMin">
                  <option value="0">00</option>
                  <option value="1">01</option>
                  <option value="2">02</option>

                  <option value="3">03</option>
                  <option value="4">04</option>
                  <option value="5">05</option>
                  <option value="6">06</option>
                  <option value="7">07</option>
                  <option value="8">08</option>

                  <option value="9">09</option>
                  <option value="10">10</option>
                  <option value="11">11</option>
                  <option value="12">12</option>
                  <option value="13">13</option>
                  <option value="14">14</option>

                  <option value="15">15</option>
                  <option value="16">16</option>
                  <option value="17">17</option>
                  <option value="18">18</option>
                  <option value="19">19</option>
                  <option value="20">20</option>

                  <option value="21">21</option>
                  <option value="22">22</option>
                  <option value="23">23</option>
                  <option value="24">24</option>
                  <option value="25">25</option>
                  <option value="26">26</option>

                  <option value="27">27</option>
                  <option value="28">28</option>
                  <option value="29">29</option>
                  <option value="30">30</option>
                  <option value="31">31</option>
                  <option value="32">32</option>

                  <option value="33">33</option>
                  <option value="34">34</option>
                  <option value="35">35</option>
                  <option value="36">36</option>
                  <option value="37">37</option>
                  <option value="38">38</option>

                  <option value="39">39</option>
                  <option value="40">40</option>
                  <option value="41">41</option>
                  <option value="42">42</option>
                  <option value="43">43</option>
                  <option value="44">44</option>

                  <option value="45">45</option>
                  <option value="46">46</option>
                  <option value="47">47</option>
                  <option value="48">48</option>
                  <option value="49">49</option>
                  <option value="50">50</option>

                  <option value="51">51</option>
                  <option value="52">52</option>
                  <option value="53">53</option>
                  <option value="54">54</option>
                  <option value="55">55</option>
                  <option value="56">56</option>

                  <option value="57">57</option>
                  <option value="58">58</option>
                  <option value="59">59</option>
                </select>
              </li>
            </ul>
          </form>

      <!-- Optional Information -->
      <!-- DublinCore metadata -->
      <div class="layout-centered ui-widget oc-ui-collapsible-widget" id="dublincore_tab" style="display:none"></div>
      
      <!-- Youtube metadata -->
      <div class="layout-centered ui-widget oc-ui-collapsible-widget" id="youtube_tab" style="display:none"></div> 
      
      <!-- Itunes metadata -->
      <div class="layout-centered ui-widget oc-ui-collapsible-widget" id="itunes_tab" style="display:none"></div> 
        </div>
      </div>

      
      <!-- file upload -->
      <div class="form-box layout-centered ui-widget oc-ui-collapsible-widget" id="files_tab" style="display:none"></div>

      <!-- Processing Instructions -->
      <div class="form-box layout-centered container ui-widget">
        <div class="form-box-head ui-widget-header ui-corner-top"><b id="i18n_process_title">Processing</b></div>

        <div class="form-box-content ui-widget-content ui-corner-bottom">
          <ul class="oc-ui-form-list">
            <li class="ui-helper-clearfix">
              <label class="scheduler-label"><span style="color: red;">* </span><span id="i18n_process_instr">Processing instructions</span>:</label>
              <select id="workflowSelector" id="workflowSelector" class="workflowSelector"></select>
            </li>
            <li class="workflowConfigContainer" id="workflowConfigContainer">

              <div class="workflowConfigPanel"></div>
            </li>
          </ul>
        </div>
      </div>

      <!-- submit/cancel controls -->
      <div class="form-box layout-centered ui-widget">
        <div class="form-box-content ui-widget-content ui-corner-all">

          <form action="">
            <ul class="oc-ui-form-list">
              <!-- submit / cancal button -->
              <li class="ui-helper-clearfix">
                <label class="scheduler-label">&#160;</label>
                <button id="submitButton" type="button" class="mouseover-pointer control-button">Upload</button>
                <button id="cancelButton" type="button" class="mouseover-pointer control-button">Cancel</button>
              </li>

              <!-- * = required -->
              <li class="ui-helper-clearfix">
                <label class="scheduler-label">&#160;</label>
                <span class="color-red">* </span><span id="i18n_required">Required</span>
              </li>
            </ul>
        </div>
      </div>

    </form>

    <!-- END of stage -->
  </div>
</div>

<div id="gray-out">
  &#160;
</div>

<div id="progressStage" class="ui-corner-all progress-stage" style="display:none;">
  <div class="progress-label-top upload-label">
    &#160;
  </div>
  <div class="progressbar ui-corner-all ui-helper-clearfix">
    <div class="progressbar-indicator ui-state-default ui-corner-all" style="width:0%;">&#160;</div>
    <div class="progressbar-label"><span id="i18n_progress"></span></div>
  </div>
  <div class="progress-label-left upload-label">&#160;</div>
  <div class="progress-label-right upload-label">&#160;</div>
</div>