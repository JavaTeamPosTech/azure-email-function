package com.postechfiap;


import com.azure.communication.email.*;
import com.azure.communication.email.models.*;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;


import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    @FunctionName("enviarEmail")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION
            )
            HttpRequestMessage<Optional<EmailDTO>> request,
            final ExecutionContext context) {

        context.getLogger().info("Iniciando processamento de feedback nativo Azure ACS.");

        // 1. Obtém o corpo da requisição (JSON -> DTO)
        EmailDTO dto = request.getBody().orElse(null);

        // Validação básica
        if (dto == null || dto.getPrioridade() == null || dto.getAluno() == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Payload inválido: aluno e prioridade são obrigatórios.")
                    .build();
        }

        // 2. Configuração do Cliente via Managed Identity
        // Certifique-se que ACS_ENDPOINT e SENDER_EMAIL estejam no Application Settings da UI
        String endpoint = System.getenv("ACS_ENDPOINT");
        String senderEmail = System.getenv("SENDER_EMAIL");

        try {
            EmailClient emailClient = new EmailClientBuilder()
                    .endpoint(endpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();

            // 3. Lógica de Negócio: Notificação de Urgência
            if ("urgente".equalsIgnoreCase(dto.getPrioridade())) {
                context.getLogger().info("Prioridade URGENTE detectada para: " + dto.getAluno());

                EmailMessage message = new EmailMessage()
                        .setSenderAddress(senderEmail)
                        .setToRecipients(new EmailAddress("admin@escola.com")) // E-mail do administrador
                        .setSubject("ALERTA URGENTE: Feedback de " + dto.getAluno())
                        .setBodyPlainText("Detalhes do Feedback:\n" +
                                "Aluno: " + dto.getAluno() + "\n" +
                                "Nota: " + dto.getNota() + "\n" +
                                "Comentário: " + dto.getComentario());

                // O envio no ACS é uma operação de longa duração (LRO), usamos o poller
                SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
                poller.waitForCompletion();

                context.getLogger().info("E-mail enviado com sucesso via ACS.");
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("Feedback processado com sucesso.")
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Erro crítico na Function: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao processar e-mail: " + e.getMessage())
                    .build();
        }
    }

    /**
     * DTO para mapeamento do JSON recebido via POST
     */
    public static class EmailDTO {
        private String aluno;
        private String comentario;
        private String prioridade;
        private int nota;

        public String getAluno() { return aluno; }
        public void setAluno(String aluno) { this.aluno = aluno; }

        public String getComentario() { return comentario; }
        public void setComentario(String comentario) { this.comentario = comentario; }

        public String getPrioridade() { return prioridade; }
        public void setPrioridade(String prioridade) { this.prioridade = prioridade; }

        public int getNota() { return nota; }
        public void setNota(int nota) { this.nota = nota; }
    }
}