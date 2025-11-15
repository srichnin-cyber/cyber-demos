package com.example.demoproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RestController;
import com.example.demoproject.service.DataMapper;
import java.util.Map;	

@RestController
@SpringBootApplication
public class DemoApplication {
	private final DataMapper dataMapper;

	public DemoApplication(DataMapper dataMapper){
		this.dataMapper=dataMapper;
	}

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@GetMapping("/hello")
	public Map<String,Object> sayHello() throws Exception {
		return dataMapper.mapData("/2.mapping-config.yml", "/2.data.json");
		
	}

}
