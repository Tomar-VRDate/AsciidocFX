package com.kodedu.config.templates;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 
 * AsciidocTemplate which can be shown in the JavaFx based configuration
 *
 */
public class AsciidocTemplateFx implements AsciidocTemplateI {

	private final StringProperty name = new SimpleStringProperty();
	private final StringProperty location = new SimpleStringProperty();
	private final StringProperty description = new SimpleStringProperty();

	@Override
	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public void setName(String name) {
		this.name.set(name);
	}

	@Override
	public String getLocation() {
		var loc = location.get();
		return loc == null ? "" : loc;
	}

	public StringProperty locationProperty() {
		return location;
	}

	public void setLocation(String location) {
		this.location.set(location);
	}
	
	@Override
	public String getDescription() {
		return description.get();
	}

	public StringProperty descriptionProperty() {
		return description;
	}

	public void setDescription(String description) {
		this.description.set(description);
	}
}
