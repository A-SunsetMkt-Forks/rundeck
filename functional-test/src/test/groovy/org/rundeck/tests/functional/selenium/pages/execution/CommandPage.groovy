package org.rundeck.tests.functional.selenium.pages.execution

import groovy.transform.CompileStatic
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.rundeck.tests.functional.selenium.pages.BasePage
import org.rundeck.util.container.SeleniumContext

/**
 * Command page
 */
@CompileStatic
class CommandPage extends BasePage {

    String loadPath = ""

    By nodeFilterTextBy = By.xpath("//*[@id=\"schedJobNodeFilter\"]")
    By filterNodeBy = By.xpath("//button[contains(@class, 'node_filter__dosearch')]")
    By commandInputTextBy = By.xpath("//input[@id='runFormExec']")
    By runBy = By.xpath("//a[@onclick=\"runFormSubmit('runbox');\"]")
    By runningExecutionStateBy = By.cssSelector("#runcontent .executionshow .execution-action-links a .execstate[data-execstate]")
    By abortBy = By.cssSelector("span[data-bind\$='killExecAction']")

    CommandPage(final SeleniumContext context, String project) {
        super(context)
        this.loadPath = "/project/${project}/command/run"
    }

    WebElement getNodeFilterTextField() {
        sleep 1000
        el nodeFilterTextBy
    }

    WebElement getFilterNodeButton() {
        sleep 1000
        el filterNodeBy
    }

    WebElement getCommandTextField() {
        sleep 1000
        el commandInputTextBy
    }

    WebElement getRunButton() {
        sleep 1000
        el runBy
    }

    WebElement getRunningExecutionStateButton() {
        el runningExecutionStateBy
    }

    WebElement getAbortButton() {
        el abortBy
    }

    WebElement runningButtonLink() {
        el runningExecutionStateBy findElement By.xpath("./..")
    }

}