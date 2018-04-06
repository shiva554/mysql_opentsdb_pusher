package com.cox.sweng.client;

import java.io.IOException;

import com.cox.sweng.client.builder.MetricBuilder;
import com.cox.sweng.client.response.Response;

public interface HttpClient extends Client {

	public Response pushMetrics(MetricBuilder builder,
			ExpectResponse exceptResponse) throws IOException;
}