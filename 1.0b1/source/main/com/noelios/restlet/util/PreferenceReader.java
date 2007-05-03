/*
 * Copyright 2005-2006 J�r�me LOUVEL
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.restlet.data.Parameter;
import org.restlet.data.Preference;

import com.noelios.restlet.data.CharacterSetImpl;
import com.noelios.restlet.data.CharacterSetPrefImpl;
import com.noelios.restlet.data.EncodingImpl;
import com.noelios.restlet.data.EncodingPrefImpl;
import com.noelios.restlet.data.LanguageImpl;
import com.noelios.restlet.data.LanguagePrefImpl;
import com.noelios.restlet.data.MediaTypeImpl;
import com.noelios.restlet.data.MediaTypePrefImpl;

/**
 * Preference header reader.<br/>
 * Works for character sets, encodings, languages or media types.
 */
public class PreferenceReader extends HeaderReader
{
   public static final int TYPE_CHARACTER_SET = 1;
   public static final int TYPE_ENCODING = 2;
   public static final int TYPE_LANGUAGE = 3;
   public static final int TYPE_MEDIA_TYPE = 4;

   /** The type of metadata read. */
   protected int type;

   /**
    * Constructor.
    * @param type The type of metadata read.
    * @param header The header to read.
    */
   public PreferenceReader(int type, String header)
   {
      super(header);
      this.type = type;
   }

   /**
    * Read the next preference.
    * @return The next preference.
    */
   public Preference readPreference() throws IOException
   {
      Preference result = null;

      boolean readingMetadata = true;
      boolean readingParamName = false;
      boolean readingParamValue = false;

      StringBuilder metadataBuffer = new StringBuilder();
      StringBuilder paramNameBuffer = null;
      StringBuilder paramValueBuffer = null;

      List<Parameter> parameters = null;
      int nextChar = 0;

      while((result == null) && (nextChar != -1))
      {
         nextChar = read();

         if(readingMetadata)
         {
            if((nextChar == ',') || (nextChar == -1))
            {
               if(metadataBuffer.length() > 0)
               {
                  // End of metadata section
                  // No parameters detected
                  result = createPreference(metadataBuffer, null);
                  paramNameBuffer = new StringBuilder();
               }
               else if(nextChar == -1)
               {
                  // Do nothing return null preference
               }
               else
               {
                  throw new IOException("Empty metadata name detected.");
               }
            }
            else if(nextChar == ';')
            {
               if(metadataBuffer.length() > 0)
               {
                  // End of metadata section
                  // Parameters detected
                  readingMetadata = false;
                  readingParamName = true;
                  paramNameBuffer = new StringBuilder();
                  parameters = new ArrayList<Parameter>();
               }
               else
               {
                  throw new IOException("Empty metadata name detected.");
               }
            }
            else if(nextChar == ' ')
            {
               // Ignore white spaces
            }
            else if(HeaderUtils.isText(nextChar))
            {
               metadataBuffer.append((char)nextChar);
            }
            else
            {
               throw new IOException("Control characters are not allowed within a metadata name.");
            }
         }
         else if(readingParamName)
         {
            if(nextChar == '=')
            {
               if(paramNameBuffer.length() > 0)
               {
                  // End of parameter name section
                  readingParamName = false;
                  readingParamValue = true;
                  paramValueBuffer = new StringBuilder();
               }
               else
               {
                  throw new IOException("Empty parameter name detected.");
               }
            }
            else if((nextChar == ',') || (nextChar == -1))
            {
               if(paramNameBuffer.length() > 0)
               {
                  // End of parameters section
                  parameters.add(createParameter(paramNameBuffer, null));
                  result = createPreference(metadataBuffer, parameters);
               }
               else
               {
                  throw new IOException("Empty parameter name detected.");
               }
            }
            else if(nextChar == ';')
            {
               // End of parameter
               parameters.add(createParameter(paramNameBuffer, null));
               paramNameBuffer = new StringBuilder();
               readingParamName = true;
               readingParamValue = false;
            }
            else if((nextChar == ' ') && (paramNameBuffer.length() == 0))
            {
               // Ignore white spaces
            }
            else if(HeaderUtils.isTokenChar(nextChar))
            {
               paramNameBuffer.append((char)nextChar);
            }
            else
            {
               throw new IOException("Separator and control characters are not allowed within a token.");
            }
         }
         else if(readingParamValue)
         {
            if((nextChar == ',') || (nextChar == -1))
            {
               if(paramValueBuffer.length() > 0)
               {
                  // End of parameters section
                  parameters.add(createParameter(paramNameBuffer, paramValueBuffer));
                  result = createPreference(metadataBuffer, parameters);
               }
               else
               {
                  throw new IOException("Empty parameter value detected");
               }
            }
            else if(nextChar == ';')
            {
               // End of parameter
               parameters.add(createParameter(paramNameBuffer, paramValueBuffer));
               paramNameBuffer = new StringBuilder();
               readingParamName = true;
               readingParamValue = false;
            }
            else if((nextChar == '"') && (paramValueBuffer.length() == 0))
            {
               paramValueBuffer.append(readQuotedString());
            }
            else if(HeaderUtils.isTokenChar(nextChar))
            {
               paramValueBuffer.append((char)nextChar);
            }
            else
            {
               throw new IOException("Separator and control characters are not allowed within a token");
            }
         }
      }

      return result;
   }

   /**
    * Extract the media parameters. Only leaveas the quality parameter if found. Modifies the parameters list.
    * @param parameters All the preference parameters.
    * @return The media parameters.
    */
   protected List<Parameter> extractMediaParams(List<Parameter> parameters)
   {
      List<Parameter> result = null;
      boolean qualityFound = false;
      Parameter param = null;

      if(parameters != null)
      {
         result = new ArrayList<Parameter>();

         for(Iterator iter = parameters.iterator(); !qualityFound && iter.hasNext();)
         {
            param = (Parameter)iter.next();

            if(param.getName().equals("q"))
            {
               qualityFound = true;
            }
            else
            {
               iter.remove();
               result.add(param);
            }
         }
      }

      return result;
   }

   /**
    * Extract the quality value. If the value is not found, 1 is returned.
    * @param parameters The preference parameters.
    * @return The quality value.
    */
   protected float extractQuality(List<Parameter> parameters)
   {
      float result = 1F;
      boolean found = false;

      if(parameters != null)
      {
         Parameter param = null;
         for(Iterator iter = parameters.iterator(); !found && iter.hasNext();)
         {
            param = (Parameter)iter.next();
            if(param.getName().equals("q"))
            {
               result = PreferenceUtils.parseQuality(param.getValue());
               found = true; 

               // Remove the quality parameter as we will directly store it
               // in the Preference object
               iter.remove();
            }
         }
      }

      return result;
   }

   /**
    * Creates a new preference.
    * @param metadata The metadata name.
    * @param parameters The parameters list.
    * @return The new preference.
    */
   protected Preference createPreference(CharSequence metadata, List<Parameter> parameters)
   {
      Preference result = null;

      if(parameters == null)
      {
         switch(type)
         {
            case TYPE_CHARACTER_SET:
               result = new CharacterSetPrefImpl(new CharacterSetImpl(metadata.toString()));
               break;

            case TYPE_ENCODING:
               result = new EncodingPrefImpl(new EncodingImpl(metadata.toString()));
               break;

            case TYPE_LANGUAGE:
               result = new LanguagePrefImpl(new LanguageImpl(metadata.toString()));
               break;

            case TYPE_MEDIA_TYPE:
               result = new MediaTypePrefImpl(new MediaTypeImpl(metadata.toString()));
               break;
         }
      }
      else
      {
         List<Parameter> mediaParams = extractMediaParams(parameters);
         float quality = extractQuality(parameters);
         
         switch(type)
         {
            case TYPE_CHARACTER_SET:
               result = new CharacterSetPrefImpl(new CharacterSetImpl(metadata.toString()), quality, parameters);
               break;

            case TYPE_ENCODING:
               result = new EncodingPrefImpl(new EncodingImpl(metadata.toString()), quality, parameters);
               break;

            case TYPE_LANGUAGE:
               result = new LanguagePrefImpl(new LanguageImpl(metadata.toString()), quality, parameters);
               break;

            case TYPE_MEDIA_TYPE:
               result = new MediaTypePrefImpl(new MediaTypeImpl(metadata.toString(), mediaParams), quality, parameters);
               break;
         }
      }

      return result;
   }

}
