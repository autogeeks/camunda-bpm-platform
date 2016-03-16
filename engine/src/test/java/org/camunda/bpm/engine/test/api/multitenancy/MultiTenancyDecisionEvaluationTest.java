/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.test.api.multitenancy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;

public class MultiTenancyDecisionEvaluationTest extends PluggableProcessEngineTestCase {

  protected static final String DMN_FILE = "org/camunda/bpm/engine/test/api/dmn/Example.dmn";
  protected static final String DMN_FILE_SECOND_VERSION = "org/camunda/bpm/engine/test/api/dmn/Example_v2.dmn";

  protected static final String DECISION_DEFINITION_KEY = "decision";

  protected static final String TENANT_ONE = "tenant1";
  protected static final String TENANT_TWO = "tenant2";

  protected static final String RESULT_OF_FIRST_VERSION = "ok";
  protected static final String RESULT_OF_SECOND_VERSION = "notok";

  public void testFailToEvaluateDecisionByIdWithoutTenantId() {
    deployment(DMN_FILE);

    DecisionDefinition decisionDefinition = repositoryService.createDecisionDefinitionQuery().singleResult();

    try {
      decisionService.evaluateDecisionTableById(decisionDefinition.getId())
          .variables(createVariables())
          .decisionDefinitionWithoutTenantId()
          .evaluate();
      fail("BadUserRequestException exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  public void testFailToEvaluateDecisionByIdWithTenantId() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);

    DecisionDefinition decisionDefinition = repositoryService.createDecisionDefinitionQuery().singleResult();

    try {
      decisionService.evaluateDecisionTableById(decisionDefinition.getId())
          .variables(createVariables())
          .decisionDefinitionTenantId(TENANT_ONE)
          .evaluate();
      fail("BadUserRequestException exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  public void testFailToEvaluateDecisionByKeyForNonExistingTenantID() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);
    deploymentForTenant(TENANT_TWO, DMN_FILE);

    try {
      decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
          .variables(createVariables())
          .decisionDefinitionTenantId("nonExistingTenantId")
          .evaluate();
      fail("ProcessEngineException expected");
    } catch (ProcessEngineException e) {
      assertThat(e.getMessage(), containsString("no decision definition deployed with key 'decision' and tenant-id 'nonExistingTenantId'"));
    }
  }

  public void testFailToEvaluateDecisionByKeyForMultipleTenants() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);
    deploymentForTenant(TENANT_TWO, DMN_FILE);

    try {
      decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
          .variables(createVariables())
          .evaluate();
      fail("ProcessEngineException expected");
    } catch (ProcessEngineException e) {
      assertThat(e.getMessage(), containsString("multiple tenants."));
    }
  }

  public void testEvaluateDecisionByKeyWithoutTenantId() {
    deployment(DMN_FILE);

    DmnDecisionTableResult decisionResult = decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
        .variables(createVariables())
        .decisionDefinitionWithoutTenantId()
        .evaluate();

    assertThatDecisionHasResult(decisionResult, RESULT_OF_FIRST_VERSION);
  }

  public void testEvaluateDecisionByKeyForAnyTenants() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);

    DmnDecisionTableResult decisionResult = decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
        .variables(createVariables())
        .evaluate();

    assertThatDecisionHasResult(decisionResult, RESULT_OF_FIRST_VERSION);
  }


  public void testEvaluateDecisionByKeyAndTenantId() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);
    deploymentForTenant(TENANT_TWO, DMN_FILE);

    DmnDecisionTableResult decisionResult = decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
        .variables(createVariables())
        .decisionDefinitionTenantId(TENANT_ONE)
        .evaluate();

    assertThatDecisionHasResult(decisionResult, RESULT_OF_FIRST_VERSION);
  }

  public void testEvaluateDecisionByKeyLatestVersionAndTenantId() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);
    deploymentForTenant(TENANT_ONE, DMN_FILE_SECOND_VERSION);

    DmnDecisionTableResult decisionResult = decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
        .variables(createVariables())
        .decisionDefinitionTenantId(TENANT_ONE)
        .evaluate();

    assertThatDecisionHasResult(decisionResult, RESULT_OF_SECOND_VERSION);
  }

  public void testEvaluateDecisionByKeyVersionAndTenantId() {
    deploymentForTenant(TENANT_ONE, DMN_FILE);

    deploymentForTenant(TENANT_TWO, DMN_FILE_SECOND_VERSION);
    deploymentForTenant(TENANT_TWO, DMN_FILE_SECOND_VERSION);

    DmnDecisionTableResult decisionResult = decisionService.evaluateDecisionTableByKey(DECISION_DEFINITION_KEY)
        .variables(createVariables())
        .version(2)
        .decisionDefinitionTenantId(TENANT_TWO)
        .evaluate();

    assertThatDecisionHasResult(decisionResult, RESULT_OF_SECOND_VERSION);
  }

  protected VariableMap createVariables() {
    return Variables.createVariables().putValue("status", "silver").putValue("sum", 723);
  }

  protected void assertThatDecisionHasResult(DmnDecisionTableResult decisionResult, Object expectedValue) {
    assertThat(decisionResult, is(notNullValue()));
    assertThat(decisionResult.size(), is(1));
    String value = decisionResult.getSingleResult().getFirstEntry();
    assertThat(value, is(expectedValue));
  }

}
