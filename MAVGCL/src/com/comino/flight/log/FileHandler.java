/****************************************************************************
 *
 *   Copyright (c) 2016 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.prefs.Preferences;

import com.comino.flight.log.px4log.PX4toModelConverter;
import com.comino.flight.log.ulog.UlogtoModelConverter;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.parameter.PX4Parameters;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.mav.control.IMAVController;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javafx.scene.Cursor;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import me.drton.jmavlib.log.px4.PX4LogReader;
import me.drton.jmavlib.log.ulog.ULogReader;


public class FileHandler {

	private static final String BASEPATH = "/.MAVGCL";
	private static final String TMPFILE  =  "/logtmp.tmp";

	private static FileHandler handler = null;

	private Stage stage;
	private String name="";
	private Preferences userPrefs;

	private AnalysisModelService modelService = AnalysisModelService.getInstance();


	public static FileHandler getInstance() {
		return handler;
	}

	public static FileHandler getInstance(Stage stage, IMAVController control) {
		if(handler==null)
			handler = new FileHandler(stage,control);
		return handler;
	}

	private FileHandler(Stage stage, IMAVController control) {
		super();
		this.stage = stage;
		this.userPrefs = MAVPreferences.getInstance();
	}

	public String getName() {
			return name;
	}

	public void clear() {
		name = "";
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBasePath() {
		return System.getProperty("user.home")+BASEPATH;
	}

	public void fileImport() {
		FileChooser fileChooser = getFileDialog("Open MAVGCL model file...",
				new ExtensionFilter("MAVGCL Model Files", "*.mgc"));

		File file = fileChooser.showOpenDialog(stage);
		try {
			if(file!=null) {
				Type listType = new TypeToken<ArrayList<AnalysisDataModel>>() {}.getType();
				Reader reader = new FileReader(file);
				Gson gson = new GsonBuilder().create();
				stage.getScene().setCursor(Cursor.WAIT); //Change cursor to wait style
				ArrayList<AnalysisDataModel>modelList = gson.fromJson(reader,listType);
				reader.close();
				modelService.setModelList(modelList);
				stage.getScene().setCursor(Cursor.DEFAULT);
				name = file.getName();
				StateProperties.getInstance().getLogLoadedProperty().set(true);

			}
		} catch (IOException e) {
			System.err.println(this.getClass().getSimpleName()+":"+e.getMessage());
		}
	}


	public void fileImportLog() {
		FileChooser fileChooser = getFileDialog("Import data ...",
				new ExtensionFilter("ULog Files", "*.ulg"),
				new ExtensionFilter("PX4Log Files", "*.px4log"),
				new ExtensionFilter("MAVGCL Files","*.mgc"));

		File file = fileChooser.showOpenDialog(stage);
		try {
			if(file!=null) {
				stage.getScene().setCursor(Cursor.WAIT);
				if(file.getName().endsWith("px4log")) {
				   PX4LogReader reader = new PX4LogReader(file.getAbsolutePath());
				   PX4Parameters.getInstance().setParametersFromLog(reader.getParameters());
				   PX4toModelConverter converter = new PX4toModelConverter(reader,modelService.getModelList());
				   converter.doConversion();
				   StateProperties.getInstance().getLogLoadedProperty().set(true);
				}

				if(file.getName().endsWith("ulg")) {
					  ULogReader reader = new ULogReader(file.getAbsolutePath());
					  PX4Parameters.getInstance().setParametersFromLog(reader.getParameters());
					  UlogtoModelConverter converter = new UlogtoModelConverter(reader,modelService.getModelList());
					  converter.doConversion();
					  StateProperties.getInstance().getLogLoadedProperty().set(true);
				}

				if(file.getName().endsWith("mgc")) {
					Type listType = new TypeToken<ArrayList<AnalysisDataModel>>() {}.getType();
					Reader reader = new FileReader(file);
					Gson gson = new GsonBuilder().create();
					stage.getScene().setCursor(Cursor.WAIT); //Change cursor to wait style
					ArrayList<AnalysisDataModel>modelList = gson.fromJson(reader,listType);
					reader.close();
					modelService.setModelList(modelList);
					StateProperties.getInstance().getLogLoadedProperty().set(true);
				}

				stage.getScene().setCursor(Cursor.DEFAULT);
				name = file.getName();

			}
		} catch (Exception e) {
			System.err.println(this.getClass().getSimpleName()+":"+e.getMessage());
		}

	}


	public void fileExport() {

		FileChooser fileChooser = getFileDialog("Save to MAVGCL model file...",
				new ExtensionFilter("MAVGCL Model Files", "*.mgc"));

		if(name.length()<2)
		      name = new SimpleDateFormat("ddMMyy-HHmmss'.mgc'").format(new Date());

		fileChooser.setInitialFileName(name);
		File file = fileChooser.showSaveDialog(stage);
		try {
			if(file!=null) {
				Writer writer = new FileWriter(file);
				Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
				stage.getScene().setCursor(Cursor.WAIT);
				gson.toJson(modelService.getModelList(), writer);
				writer.close();
				stage.getScene().setCursor(Cursor.DEFAULT);
				name = file.getName();

			}
		} catch (IOException e) {
			System.err.println(this.getClass().getSimpleName()+":"+e.getMessage());
		}

	}


	public void autoSave() throws IOException {
		stage.getScene().setCursor(Cursor.WAIT);
		name = new SimpleDateFormat("ddMMyy-HHmmss'.mgc'").format(new Date());
		String path = userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home"));
		File f = new File(path+"/"+name);
		System.out.println("Autosave to "+f.getPath());
		if(f.exists())
			f.delete();
		f.createNewFile();
		Writer writer = new FileWriter(f);
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		stage.getScene().setCursor(Cursor.WAIT);
		gson.toJson(modelService.getModelList(), writer);
		writer.close();
		stage.getScene().setCursor(Cursor.DEFAULT);
	}


	public File getTempFile() throws IOException {
		File f = new File(getBasePath()+TMPFILE);
		if(f.exists())
			f.delete();
		f.createNewFile();
		return f;

	}



	private FileChooser getFileDialog(String title, ExtensionFilter...filter) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().addAll(filter);
		fileChooser.setInitialDirectory(
				new File(userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home"))));
		return fileChooser;
	}

}
