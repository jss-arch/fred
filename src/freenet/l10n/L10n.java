/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.l10n;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;

import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

/**
 * This class provides a trivial internationalization framework to a freenet node.
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 * 
 * TODO: Maybe we ought to use the locale to set the default language.
 * TODO: Maybe base64 the override file ?
 * 
 * comment(mario): for www interface we might detect locale from http requests?
 * for other access (telnet) using system locale would probably be good, but
 * it would be nice to have a command to switch locale on the fly.
 */

public class L10n {
	public static final String CLASS_NAME = "L10n";
	public static final String PREFIX = "freenet.l10n.";
	public static final String SUFFIX = ".properties";
	public static final String OVERRIDE_SUFFIX = ".override" + SUFFIX;
	
	// English has to remain the first one!
	public static final String[] AVAILABLE_LANGUAGES = { "en", "fr", "pl"};
	private String selectedLanguage = AVAILABLE_LANGUAGES[0];
	
	private static SimpleFieldSet currentTranslation = null;
	private static SimpleFieldSet fallbackTranslation = null;
	private static L10n currentClass = null;
	
	private static SimpleFieldSet translationOverride = null;

	L10n(String selected) {
		selectedLanguage = selected;
		File tmpFile = new File(L10n.PREFIX + selected + L10n.OVERRIDE_SUFFIX);
		
		try {
			if(tmpFile.exists() && tmpFile.canRead()) {
				Logger.normal(this, "Override file detected : let's try to load it");
				translationOverride = SimpleFieldSet.readFrom(tmpFile, true, false);
			}
		} catch (IOException e) {
			translationOverride = null;
			Logger.error(this, "IOError while accessing the file!" + e.getMessage(), e);
		}
		currentTranslation = loadTranslation(selectedLanguage);
	}
	
	/**
	 * Set the default language used by the framework.
	 * 
	 * @param selectedLanguage (2 letter code)
	 * @throws MissingResourceException
	 */
	public static void setLanguage(String selectedLanguage) throws MissingResourceException {
		for(int i=0; i<AVAILABLE_LANGUAGES.length; i++){
			if(selectedLanguage.equalsIgnoreCase(AVAILABLE_LANGUAGES[i])){
				selectedLanguage = AVAILABLE_LANGUAGES[i];
				Logger.normal(CLASS_NAME, "Changing the current language to : " + selectedLanguage);
				currentClass = new L10n(selectedLanguage);
				if(currentTranslation == null) {
					currentClass = new L10n(AVAILABLE_LANGUAGES[0]);
					throw new MissingResourceException("Unable to load the translation file for "+selectedLanguage, "l10n", selectedLanguage);
				}
				return;
			}
		}
		
		currentClass = new L10n(AVAILABLE_LANGUAGES[0]);
		Logger.error(CLASS_NAME, "The requested translation is not available!" + selectedLanguage);
		throw new MissingResourceException("The requested translation ("+selectedLanguage+") hasn't been found!", CLASS_NAME, selectedLanguage);
	}
	
	public static void setOverride(String key, String value) {
		key = key.trim();
		value = value.trim();
		
		// If there is no need to keep it in the override, remove it
		if("".equals(value) || L10n.getString(key).equals(value)) {
			translationOverride.removeValue(key);
		} else {
			// Is the override already declared ? if not, create it.
			if(translationOverride == null)
				translationOverride = new SimpleFieldSet(false);
			
			// Set the value of the override
			translationOverride.putOverwrite(key, value);
			Logger.normal("L10n", "Got a new translation key: set the Override!");
		}
		// Save the file to disk
		_saveTranslationFile();
	}
	
	private synchronized static void _saveTranslationFile() {
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		
		try {
			fos = new FileOutputStream(new File(L10n.PREFIX + L10n.getSelectedLanguage() + L10n.OVERRIDE_SUFFIX));
			bos = new BufferedOutputStream(fos);			
			
			bos.write(L10n.translationOverride.toOrderedString().getBytes("UTF-8"));
			Logger.normal("L10n", "Override file saved successfully!");
		} catch (IOException e) {
			Logger.error("L10n", "Error while saving the translation override: "+ e.getMessage(), e);
		} finally {
			try {
				if(bos != null) bos.close();
				if(fos != null) fos.close();
			} catch (IOException e) {}
		}
	}
	
	public static SimpleFieldSet getCurrentLanguageTranslation() {
		return currentTranslation;
	}
	
	public static SimpleFieldSet getOverrideForCurrentLanguageTranslation() {
		return translationOverride;
	}
	
	/**
	 * The real meat
	 * 
	 * Same thing as getString(key, false);
	 * Ensure it will *always* return a String value.
	 * 
	 * @param key
	 * @return the translated string or the default value from the default language or the key if nothing is found
	 */
	public static String getString(String key) {
		return getString(key, false);
	}
	
	/**
	 * You probably don't want to use that one directly
	 * @see getString(String)
	 */
	public static String getString(String key, boolean returnNullIfNotFound) {
		String result = (translationOverride == null ? null : translationOverride.get(key));
		if(result != null) return result;
		
		result = currentTranslation.get(key);
		if(result != null)
			return result;
		else
			return (returnNullIfNotFound ? null : getDefaultString(key));
	}
	
	/**
	 * Almost the same as getString(String) ... but it returns a HTMLNode and gives the user the ability to contribute to the translation
	 * @param key
	 * @return HTMLNode
	 */
	public static HTMLNode getHTMLNode(String key) {
		String value = getString(key, true);
		if(value != null)
			return new HTMLNode("#", value);
		HTMLNode translationField = new HTMLNode("span", "class", "translate_it") ;
		translationField.addChild("#", getDefaultString(key));
		translationField.addChild("a", "href", "/?translate=" + key).addChild("small", " (translate it in your native language!)");
			
		return translationField;
	}
	
	public static String getDefaultString(String key) {
		String result = null;
		// We instanciate it only if necessary
		if(fallbackTranslation == null) fallbackTranslation = loadTranslation(AVAILABLE_LANGUAGES[0]);
		
		result = fallbackTranslation.get(key);
		
		if(result != null) {
			Logger.normal(CLASS_NAME, "The translation for " + key + " hasn't been found! please tell the maintainer.");
			return result; 
		}
		Logger.error(CLASS_NAME, "The translation for " + key + " hasn't been found!");
		return key;
	}
	
	/**
	 * Allows things like :
	 * L10n.getString("testing.test", new String[]{ "test1", "test2" }, new String[] { "a", "b" })
	 * 
	 * @param key
	 * @param patterns : a list of patterns wich are matchable from the translation
	 * @param values : the values corresponding to the list
	 * @return the translated string or the default value from the default language or the key if nothing is found
	 */
	public static String getString(String key, String[] patterns, String[] values) {
		assert(patterns.length == values.length);
		String result = getString(key);

		for(int i=0; i<patterns.length; i++)
				result = result.replaceAll("\\$\\{"+patterns[i]+"\\}", quoteReplacement(values[i]));
		
		return result;
	}
	
	private static String quoteReplacement(String s) {
		if ((s.indexOf('\\') == -1) && (s.indexOf('$') == -1))
			return s;
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\') {
				sb.append('\\');
				sb.append('\\');
			} else if (c == '$') {
				sb.append('\\');
				sb.append('$');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	public static String getSelectedLanguage() {
		return currentClass.selectedLanguage;
	}
	
	/**
	 * Load a translation file depending on the given name and using the prefix
	 * 
	 * @param name
	 * @return the Properties object or null if not found
	 */
	public static SimpleFieldSet loadTranslation(String name) {
        name = PREFIX.replace ('.', '/').concat(PREFIX.concat(name.concat(SUFFIX)));
        
        SimpleFieldSet result = null;
        InputStream in = null;
        try {
        	ClassLoader loader = ClassLoader.getSystemClassLoader();
        	
        	// Returns null on lookup failures:
        	in = loader.getResourceAsStream(name);
        	if(in != null)
        		result = SimpleFieldSet.readFrom(in, false, false);
        } catch (Exception e) {
        	Logger.error("L10n", "Error while loading the l10n file from " + name + " :" + e.getMessage());
            result = null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignore) {}
        }
        
        return result;
    }
		
	public static void main(String[] args) {
		L10n.setLanguage("en");
		System.out.println(L10n.getString("QueueToadlet.failedToRestart"));
		L10n.setLanguage("fr");
		System.out.println(L10n.getString("QueueToadlet.failedToRestart"));
		//System.out.println(L10n.getString("testing.test", new String[]{ "test1", "test2" }, new String[] { "a", "b" }));
	}
}
