/**
 * Copyright 2017 Ambud Sharma
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kafka.manager.base;

import java.util.Arrays;
import java.util.List;

import org.apache.curator.framework.api.ACLProvider;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;

/**
 * @author ambud
 */
public class SASLAclProvider implements ACLProvider {

	private final List<ACL> saslACL;

	public SASLAclProvider(String principal) {
		this.saslACL = Arrays.asList(new ACL(Perms.ALL, new Id("sasl", principal)),
				new ACL(Perms.READ, new Id("world", "anyone")));
	}

	@Override
	public List<ACL> getDefaultAcl() {
		return saslACL;
	}

	@Override
	public List<ACL> getAclForPath(String path) {
		return saslACL;
	}

}
