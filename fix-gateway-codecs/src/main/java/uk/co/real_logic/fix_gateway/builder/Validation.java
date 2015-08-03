/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.builder;

public final class Validation
{
    public static final String VALIDATION_DISABLED_PROP = "fix.codecs.no_validation";
    public static final boolean VALIDATION_DISABLED = Boolean.getBoolean(VALIDATION_DISABLED_PROP);
    public static final boolean VALIDATION_ENABLED = !VALIDATION_DISABLED;

    public static boolean isValidMsgType(final char[] msgType, final int msgTypeLength)
    {
        for (int i = 0; i < msgTypeLength; i++)
        {
            if (!Character.isLetterOrDigit(msgType[i]))
            {
                return false;
            }
        }
        return true;
    }
}