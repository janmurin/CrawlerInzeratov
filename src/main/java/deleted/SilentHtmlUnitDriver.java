/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package deleted;

import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

public class SilentHtmlUnitDriver extends HtmlUnitDriver {

    public SilentHtmlUnitDriver() {
        super();
        this.getWebClient().setCssErrorHandler(new SilentCssErrorHandler());
    }
}
